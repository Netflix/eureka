package com.netflix.eureka2.ext.aws;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.s3.AmazonS3Client;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.EurekaRegistrationProcessorStub;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.registry.EvictionQuotaKeeper;
import com.netflix.eureka2.server.registry.RegistrationChannelProcessorProvider;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.overrides.OverridesService;
import com.netflix.eureka2.testkit.aws.MockAutoScalingService;
import com.netflix.eureka2.testkit.aws.MockS3Service;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class CombinedStatusOverridesTest {

    private static final long REFRESH_INTERVAL_SEC = 30;

    private static final InstanceInfo SEED = SampleInstanceInfo.WebServer.builder()
            .withStatus(InstanceInfo.Status.UP).build();

    private static final InstanceInfo IS_OOS = new InstanceInfo.Builder()
            .withInstanceInfo(SEED).withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();
    private static final InstanceInfo NOT_OOS = new InstanceInfo.Builder()
            .withInstanceInfo(SEED).withStatus(InstanceInfo.Status.UP).build();

    private static final Source SOURCE = new Source(Source.Origin.LOCAL, "connection#1");

    private final SelfInfoResolver selfInfoResolver = new SelfInfoResolver() {
        @Override
        public Observable<InstanceInfo> resolve() {
            return Observable.just(SampleInstanceInfo.EurekaWriteServer.build());
        }
    };

    private final TestScheduler testScheduler = Schedulers.test();

    private final AwsConfiguration asgConfig = mock(AwsConfiguration.class);
    private final S3OverridesConfiguration s3Config = mock(S3OverridesConfiguration.class);

    private final EurekaRegistrationProcessorStub registrationDelegate = new EurekaRegistrationProcessorStub();
    private final PublishSubject<InstanceInfo> registrationSubject = PublishSubject.create();

    private final MockS3Service mockS3Service = new MockS3Service();
    private final MockAutoScalingService mockAutoScalingService = new MockAutoScalingService();
    private S3StatusOverridesRegistry s3Overrides;
    private AsgStatusOverridesView asgOverrides;

    private EurekaRegistrationProcessor<InstanceInfo> processor;

    @Before
    public void setUp() throws Exception {
        when(asgConfig.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);
        when(s3Config.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);
        when(s3Config.getBucketName()).thenReturn("myBucketName");
        when(s3Config.getPrefix()).thenReturn("eureka2.overrides.test");

        AmazonS3Client amazonS3Client = mockS3Service.getAmazonS3Client();
        AmazonAutoScaling amazonAutoScaling = mockAutoScalingService.getAmazonAutoScaling();
        s3Overrides = new S3StatusOverridesRegistry(amazonS3Client, s3Config, testScheduler);
        asgOverrides = new AsgStatusOverridesView(selfInfoResolver, amazonAutoScaling, asgConfig, testScheduler);

        s3Overrides.start();
        asgOverrides.start();

        Map<Integer, OverridesService> overridesServiceMap = new HashMap<>();
        overridesServiceMap.put(0, new AsgStatusOverridesService(asgOverrides));
        overridesServiceMap.put(1, new S3StatusOverridesService(s3Overrides));

        EvictionQuotaKeeper keeper = mock(EvictionQuotaKeeper.class);
        when(keeper.quota()).thenReturn(Observable.<Long>never());

        RegistrationChannelProcessorProvider combined = new RegistrationChannelProcessorProvider(
                registrationDelegate,
                overridesServiceMap,
                keeper,
                EurekaRegistryMetricFactory.registryMetrics()
        );

        processor = combined.get();

        processor.register(SEED.getId(), registrationSubject, SOURCE).subscribe();
    }

    @After
    public void tearDown() throws Exception {
        s3Overrides.stop();
        asgOverrides.stop();
        processor.shutdown();
    }

    @Test
    public void testOOSInAsgButNotInS3() throws Exception {
        // initial register
        registrationSubject.onNext(SEED);
        registrationDelegate.verifyRegisteredWith(NOT_OOS);

        mockAutoScalingService.disableAsg(SEED.getAsg());
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        registrationDelegate.verifyRegisteredWith(IS_OOS);
    }

    @Test
    public void testOOSInS3ButNotInAsg() throws Exception {
        // initial register
        registrationSubject.onNext(SEED);
        registrationDelegate.verifyRegisteredWith(NOT_OOS);

        s3Overrides.setOutOfService(SEED).subscribe();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        registrationDelegate.verifyRegisteredWith(IS_OOS);
    }

    @Test
    public void testOOSInBoth() throws Exception {
        // initial register
        registrationSubject.onNext(SEED);
        registrationDelegate.verifyRegisteredWith(NOT_OOS);

        s3Overrides.setOutOfService(SEED).subscribe();
        mockAutoScalingService.disableAsg(SEED.getAsg());
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        registrationDelegate.verifyRegisteredWith(IS_OOS);
    }
}

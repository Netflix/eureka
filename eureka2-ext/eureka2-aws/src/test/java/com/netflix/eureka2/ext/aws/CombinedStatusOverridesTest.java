package com.netflix.eureka2.ext.aws;

import java.util.concurrent.TimeUnit;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.s3.AmazonS3Client;
import com.netflix.eureka2.aws.MockAutoScalingService;
import com.netflix.eureka2.aws.MockS3Service;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.ChangeNotificationObservable;
import com.netflix.eureka2.registry.EurekaRegistrationProcessorStub;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.registry.EvictionQuotaKeeper;
import com.netflix.eureka2.server.registry.RegistrationChannelProcessorProvider;
import com.netflix.eureka2.server.registry.RegistrationChannelProcessorProvider.OptionalOverridesService;
import com.netflix.eureka2.server.service.overrides.CompositeOverridesService;
import com.netflix.eureka2.server.service.overrides.OverridesService;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.utils.ExtCollections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class CombinedStatusOverridesTest {

    private static final long REFRESH_INTERVAL_SEC = 30;

    private static final InstanceInfo SEED = SampleInstanceInfo.WebServer.builder()
            .withStatus(InstanceInfo.Status.UP).build();

    private static final InstanceInfo IS_OOS = InstanceModel.getDefaultModel().newInstanceInfo()
            .withInstanceInfo(SEED).withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();
    private static final InstanceInfo NOT_OOS = InstanceModel.getDefaultModel().newInstanceInfo()
            .withInstanceInfo(SEED).withStatus(InstanceInfo.Status.UP).build();

    private static final Source SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "connection#1");

    private final SelfInfoResolver selfInfoResolver = new SelfInfoResolver() {
        @Override
        public Observable<InstanceInfo> resolve() {
            return Observable.just(SampleInstanceInfo.EurekaWriteServer.build());
        }
    };

    private final TestScheduler testScheduler = Schedulers.test();

    private final AwsConfiguration config = mock(AwsConfiguration.class);

    private final EurekaRegistrationProcessorStub registrationDelegate = new EurekaRegistrationProcessorStub();
    private final ChangeNotificationObservable dataStream = ChangeNotificationObservable.create();

    private final MockS3Service mockS3Service = new MockS3Service();
    private final MockAutoScalingService mockAutoScalingService = new MockAutoScalingService();
    private S3StatusOverridesRegistry s3Overrides;
    private AsgStatusOverridesView asgOverrides;

    private EurekaRegistrationProcessor<InstanceInfo> processor;

    @Before
    public void setUp() throws Exception {
        when(config.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);
        when(config.getBucketName()).thenReturn("myBucketName");
        when(config.getPrefix()).thenReturn("eureka2.overrides.test");

        AmazonS3Client amazonS3Client = mockS3Service.getAmazonS3Client();
        AmazonAutoScaling amazonAutoScaling = mockAutoScalingService.getAmazonAutoScaling();
        s3Overrides = new S3StatusOverridesRegistry(amazonS3Client, config, testScheduler);
        asgOverrides = new AsgStatusOverridesView(selfInfoResolver, amazonAutoScaling, config, testScheduler);

        s3Overrides.start();
        asgOverrides.start();

        OverridesService overridesService = new CompositeOverridesService(ExtCollections.asSet(
                (OverridesService) new AsgStatusOverridesService(asgOverrides),
                new S3StatusOverridesService(s3Overrides)
        ));

        OptionalOverridesService optionalOverridesService = new OptionalOverridesService();
        optionalOverridesService.setArg(overridesService);

        EvictionQuotaKeeper keeper = mock(EvictionQuotaKeeper.class);
        when(keeper.quota()).thenReturn(Observable.<Long>never());

        RegistrationChannelProcessorProvider combined = new RegistrationChannelProcessorProvider(
                registrationDelegate,
                optionalOverridesService
        );

        processor = combined.get();

        processor.connect(SEED.getId(), SOURCE, dataStream).subscribe();
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
        dataStream.register(SEED);
        registrationDelegate.verifyRegisteredWith(NOT_OOS);

        mockAutoScalingService.disableAsg(SEED.getAsg());
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        registrationDelegate.verifyRegisteredWith(IS_OOS);
    }

    @Test
    public void testOOSInS3ButNotInAsg() throws Exception {
        // initial register
        dataStream.register(SEED);
        registrationDelegate.verifyRegisteredWith(NOT_OOS);

        s3Overrides.setOutOfService(SEED).subscribe();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        registrationDelegate.verifyRegisteredWith(IS_OOS);
    }

    @Test
    public void testOOSInBoth() throws Exception {
        // initial register
        dataStream.register(SEED);
        registrationDelegate.verifyRegisteredWith(NOT_OOS);

        s3Overrides.setOutOfService(SEED).subscribe();
        mockAutoScalingService.disableAsg(SEED.getAsg());
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        registrationDelegate.verifyRegisteredWith(IS_OOS);
    }
}

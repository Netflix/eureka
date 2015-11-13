package com.netflix.eureka2.ext.aws;

import java.util.concurrent.TimeUnit;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.netflix.eureka2.aws.MockAutoScalingService;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class AsgStatusOverridesViewTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    private static final long REFRESH_INTERVAL_SEC = 30;

    private static final String ASG_A = "asg#A";

    private final TestScheduler testScheduler = Schedulers.test();

    private final SelfInfoResolver selfInfoResolver = new SelfInfoResolver() {
        @Override
        public Observable<InstanceInfo> resolve() {
            return Observable.just(SampleInstanceInfo.EurekaWriteServer.build());
        }
    };

    private final InstanceInfo asgAInfo = SampleInstanceInfo.WebServer.builder()
            .withAsg(ASG_A)
            .build();

    private final AwsConfiguration configuration = mock(AwsConfiguration.class);
    private final MockAutoScalingService mockAutoScalingService = new MockAutoScalingService();
    private AmazonAutoScaling amazonAutoScaling;
    private AsgStatusOverridesView registry;


    @Before
    public void setUp() throws Exception {
        when(configuration.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);

        amazonAutoScaling = mockAutoScalingService.getAmazonAutoScaling();
        registry = new AsgStatusOverridesView(
                selfInfoResolver,
                amazonAutoScaling,
                configuration,
                testScheduler
        );

        registry.start();
    }

    @After
    public void tearDown() throws Exception {
        registry.stop();
    }

    @Test
    public void testReturnsFalseIfNoDataProvidedFromAWS() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(asgAInfo).subscribe(testSubscriber);

        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(false));
    }

    @Test
    public void testDisabledAsgTrigger() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(asgAInfo).subscribe(testSubscriber);

        // Default value prior to AWS call
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(false));

        mockAutoScalingService.disableAsg(ASG_A);
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        verify(amazonAutoScaling, times(1)).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        assertThat(testSubscriber.takeNext(), is(true));

        // Verify receives ASG status false
        mockAutoScalingService.enableAsg(ASG_A);
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        verify(amazonAutoScaling, times(2)).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        assertThat(testSubscriber.takeNext(), is(false));
    }

    @Test
    public void testAsgSubscriptionIsSharedAcrossSubscribers() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber1 = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(asgAInfo).subscribe(testSubscriber1);

        // Default value prior to AWS call
        testScheduler.triggerActions();
        assertThat(testSubscriber1.takeNext(), is(false));

        // Verify testSubscriber1 receives status true
        mockAutoScalingService.disableAsg(ASG_A);
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(testSubscriber1.takeNext(), is(true));

        // Now add second subscriber for the same ASG. It should get status true immediately
        ExtTestSubscriber<Boolean> testSubscriber2 = new ExtTestSubscriber<>();
        registry.shouldApplyOutOfService(asgAInfo).subscribe(testSubscriber2);

        testScheduler.triggerActions();
        assertThat(testSubscriber2.takeNext(), is(true));

        // Unsubscribe first
        testSubscriber1.unsubscribe();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(registry.getSize(), is(equalTo(1)));

        // Unsubscribe second
        testSubscriber2.unsubscribe();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(registry.getSize(), is(equalTo(1)));
    }
}

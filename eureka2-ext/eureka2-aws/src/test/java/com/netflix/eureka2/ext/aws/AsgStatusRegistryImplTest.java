package com.netflix.eureka2.ext.aws;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.ext.aws.AsgStatusRegistryImpl.PROP_ADD_TO_LOAD_BALANCER;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class AsgStatusRegistryImplTest {

    private static final long REFRESH_INTERVAL_SEC = 30;

    private static final String ASG_A = "asg#A";

    private final TestScheduler testScheduler = Schedulers.test();

    private final AmazonAutoScaling amazonAutoScaling = mock(AmazonAutoScaling.class);
    private final AwsConfiguration configuration = mock(AwsConfiguration.class);

    private final AsgStatusRegistryImpl registry = new AsgStatusRegistryImpl(
            amazonAutoScaling,
            configuration,
            testScheduler
    );

    @Before
    public void setUp() throws Exception {
        when(configuration.getRefreshIntervalSec()).thenReturn(REFRESH_INTERVAL_SEC);
        registry.start();
    }

    @After
    public void tearDown() throws Exception {
        registry.stop();
    }

    @Test
    public void testReturnsStatusTrueIfNoDataProvidedFromAWS() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber = new ExtTestSubscriber<>();
        registry.asgStatusUpdates(ASG_A).subscribe(testSubscriber);

        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(true));
    }

    @Test
    public void testSubscribedToAsgStatusIsReadFromAWS() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber = new ExtTestSubscriber<>();
        registry.asgStatusUpdates(ASG_A).subscribe(testSubscriber);

        // Default value prior to AWS call
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(true));

        // Verify receives ASG status true
        when(amazonAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(createDescribeAutoScalingGroupsResult(ASG_A, true));

        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        verify(amazonAutoScaling, times(1)).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        assertThat(testSubscriber.takeNext(), is(true));

        // Verify receives ASG status false
        when(amazonAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(createDescribeAutoScalingGroupsResult(ASG_A, false));

        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        verify(amazonAutoScaling, times(2)).describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class));
        assertThat(testSubscriber.takeNext(), is(false));
    }

    @Test
    public void testAsgSubscriptionIsSharedAcrossSubscribers() throws Exception {
        ExtTestSubscriber<Boolean> testSubscriber1 = new ExtTestSubscriber<>();
        registry.asgStatusUpdates(ASG_A).subscribe(testSubscriber1);

        // Default value prior to AWS call
        testScheduler.triggerActions();
        assertThat(testSubscriber1.takeNext(), is(true));

        // Verify testSubscriber1 receives status true
        when(amazonAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenReturn(createDescribeAutoScalingGroupsResult(ASG_A, true));

        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(testSubscriber1.takeNext(), is(true));

        // Now add second subscriber for the same ASG. It should get status true immediately
        ExtTestSubscriber<Boolean> testSubscriber2 = new ExtTestSubscriber<>();
        registry.asgStatusUpdates(ASG_A).subscribe(testSubscriber2);

        testScheduler.triggerActions();
        assertThat(testSubscriber2.takeNext(), is(true));

        // Unsubscribe first
        testSubscriber1.unsubscribe();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(registry.getRegistrySize(), is(equalTo(1)));

        // Unsubscribe second
        testSubscriber2.unsubscribe();
        testScheduler.advanceTimeBy(REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);

        assertThat(registry.getRegistrySize(), is(equalTo(0)));
    }

    private static DescribeAutoScalingGroupsResult createDescribeAutoScalingGroupsResult(String asgName, boolean status) {
        DescribeAutoScalingGroupsResult asgResult = new DescribeAutoScalingGroupsResult();
        AutoScalingGroup asg = createAutoScalingGroup(asgName, status);
        asgResult.setAutoScalingGroups(Collections.singletonList(asg));
        return asgResult;
    }

    private static AutoScalingGroup createAutoScalingGroup(String asgName, boolean status) {
        AutoScalingGroup asg = new AutoScalingGroup();
        asg.setAutoScalingGroupName(asgName);
        if (status) {
            SuspendedProcess suspendedProcess = new SuspendedProcess();
            suspendedProcess.setProcessName(PROP_ADD_TO_LOAD_BALANCER);
            asg.setSuspendedProcesses(Collections.singletonList(suspendedProcess));
        }
        return asg;
    }
}
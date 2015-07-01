package com.netflix.eureka2.testkit.aws;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A mock for testing asg overrides
 *
 * @author David Liu
 */
public class MockAutoScalingService {

    private static final String PROP_ADD_TO_LOAD_BALANCER = "AddToLoadBalancer";

    // if an asg is disabled, it is present in this map
    private final ConcurrentMap<String, Boolean> asgs;
    private final AmazonAutoScaling amazonAutoScaling;

    public MockAutoScalingService() {
        this.asgs = new ConcurrentHashMap<>();
        this.amazonAutoScaling = mock(AmazonAutoScaling.class);
    }

    public Map<String, Boolean> getContentsView() {
        return Collections.unmodifiableMap(asgs);
    }

    public AmazonAutoScaling getAmazonAutoScaling() {
        setupMocks();
        return amazonAutoScaling;
    }

    public void disableAsg(String asgName) {
        asgs.put(asgName, true);
    }

    public void enableAsg(String asgName) {
        asgs.remove(asgName);
    }

    // override to setup custom mocks
    protected void setupMocks() {
        when(amazonAutoScaling.describeAutoScalingGroups(any(DescribeAutoScalingGroupsRequest.class)))
                .thenAnswer(new Answer<DescribeAutoScalingGroupsResult>() {
                    @Override
                    public DescribeAutoScalingGroupsResult answer(InvocationOnMock invocation) throws Throwable {
                        return createDescribeAutoScalingGroupsResult();
                    }
                });
    }

    private DescribeAutoScalingGroupsResult createDescribeAutoScalingGroupsResult() {
        DescribeAutoScalingGroupsResult asgResult = new DescribeAutoScalingGroupsResult();
        List<AutoScalingGroup> results = new ArrayList<>();
        for (String asgName : asgs.keySet()) {
            AutoScalingGroup asg = createAutoScalingGroup(asgName, true);
            results.add(asg);
        }
        asgResult.setAutoScalingGroups(results);
        return asgResult;
    }

    private static AutoScalingGroup createAutoScalingGroup(String asgName, boolean isDisabled) {
        AutoScalingGroup asg = new AutoScalingGroup();
        asg.setAutoScalingGroupName(asgName);
        if (isDisabled) {
            SuspendedProcess suspendedProcess = new SuspendedProcess();
            suspendedProcess.setProcessName(PROP_ADD_TO_LOAD_BALANCER);
            asg.setSuspendedProcesses(Collections.singletonList(suspendedProcess));
        }
        return asg;
    }

}

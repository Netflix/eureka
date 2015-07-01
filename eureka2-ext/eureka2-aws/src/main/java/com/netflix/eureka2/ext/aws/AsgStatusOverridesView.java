package com.netflix.eureka2.ext.aws;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An overrides source implementation that determines instance status overrides based on AWS asg
 * status. asg enabled == no override, asg disabled == override as OUT_OF_SERVICE.
 *
 * TODO: add ability to assume role into other accounts to query in different accounts.
 *
 * @author David Liu
 */
public class AsgStatusOverridesView implements InstanceStatusOverridesView {

    private static final Logger logger = LoggerFactory.getLogger(AsgStatusOverridesView.class);

    /* Visible for testing */ static final String PROP_ADD_TO_LOAD_BALANCER = "AddToLoadBalancer";

    private final AmazonAutoScaling amazonAutoScaling;
    private final AwsConfiguration configuration;

    private final SelfInfoResolver selfInfoResolver;

    private final BehaviorSubject<Map<AsgAndAccount, Boolean>> overridesSubject;

    private final Scheduler.Worker worker;
    private final RefreshTask refreshTask;

    private volatile Map<AsgAndAccount, Boolean> overridesMap;
    private volatile String defaultAccountId;
    private volatile Subscription refreshSubscription;

    @Inject
    public AsgStatusOverridesView(SelfInfoResolver selfInfoResolver, AmazonAutoScaling amazonAutoScaling, AwsConfiguration configuration) {
        this(selfInfoResolver, amazonAutoScaling, configuration, Schedulers.io());
    }

    public AsgStatusOverridesView(SelfInfoResolver selfInfoResolver, AmazonAutoScaling amazonAutoScaling, AwsConfiguration configuration, Scheduler scheduler) {
        this.selfInfoResolver = selfInfoResolver;
        this.amazonAutoScaling = amazonAutoScaling;
        this.configuration = configuration;
        this.overridesMap = new HashMap<>();
        this.overridesSubject = BehaviorSubject.create();
        this.overridesSubject.onNext(overridesMap);

        this.worker = scheduler.createWorker();
        this.refreshTask = new RefreshTask();
    }

    @PostConstruct
    public void start() {
        selfInfoResolver.resolve()
                .take(1)
                .doOnNext(new Action1<InstanceInfo>() {
                    @Override
                    public void call(InstanceInfo instanceInfo) {
                        DataCenterInfo dataCenterInfo = instanceInfo.getDataCenterInfo();
                        if (dataCenterInfo instanceof AwsDataCenterInfo) {
                            defaultAccountId = ((AwsDataCenterInfo) dataCenterInfo).getAccountId();
                        }

                        scheduleRefresh();  // refresh regardless
                    }
                })
                .subscribe(new Subscriber<InstanceInfo>() {
                    @Override
                    public void onCompleted() {
                        logger.info("AsgStatusOverridesRegistry initialized");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.info("AsgStatusOverridesRegistry initialization failed", e);
                    }

                    @Override
                    public void onNext(InstanceInfo instanceInfo) {

                    }
                });
    }

    @PreDestroy
    public void stop() {
        if (refreshSubscription != null) {
            refreshSubscription.unsubscribe();
            worker.unsubscribe();
        }
    }

    @Override
    public Observable<Boolean> shouldApplyOutOfService(final InstanceInfo instanceInfo) {
        return overridesSubject
                .map(new Func1<Map<AsgAndAccount, Boolean>, Boolean>() {
                    @Override
                    public Boolean call(Map<AsgAndAccount, Boolean> map) {
                        if (! (instanceInfo.getDataCenterInfo() instanceof AwsDataCenterInfo) ) {
                            return false;
                        }

                        String asgName = instanceInfo.getAsg();
                        String accountId = ((AwsDataCenterInfo)instanceInfo.getDataCenterInfo()).getAccountId();
                        if (accountId == null) {
                            accountId = defaultAccountId;
                        }

                        AsgAndAccount asgAndAccount = new AsgAndAccount(asgName, accountId);
                        return map.containsKey(asgAndAccount);
                    }
                })
                .distinctUntilChanged()
                .cache(1);
    }

    /**
     * Provided for testing.
     */
    int getSize() {
        return overridesMap.size();
    }

    private void scheduleRefresh() {
        long refreshInterval = TimeUnit.SECONDS.toMillis(Math.max(1, configuration.getRefreshIntervalSec()));
        refreshSubscription = worker.schedule(refreshTask, refreshInterval, TimeUnit.MILLISECONDS);
    }

    private class RefreshTask implements Action0 {

        private static final int PAGE_SIZE = 100;

        @Override
        public void call() {
            try {
                doRefresh();
            } catch (Throwable e) {
                logger.error("ASG status refresh failed", e);
            }
            scheduleRefresh();
        }

        /**
         * Read new ASG status updates from AWS.
         */
        private void doRefresh() {
            Map<AsgAndAccount, Boolean> asgSnapshot = new HashMap<>();

            int total = 0;
            String nextToken = null;
            try {
                do {
                    DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
                    request.setMaxRecords(PAGE_SIZE);
                    request.setNextToken(nextToken);
                    DescribeAutoScalingGroupsResult result = amazonAutoScaling.describeAutoScalingGroups(request);

                    for (AutoScalingGroup asg : result.getAutoScalingGroups()) {
                        if (isAddToLoadBalancerSuspended(asg)) {
                            asgSnapshot.put(new AsgAndAccount(asg.getAutoScalingGroupName(), defaultAccountId), true);
                        }
                    }

                    nextToken = result.getNextToken();
                    total += result.getAutoScalingGroups().size();
                } while (nextToken != null);

                logger.debug("Retrieved {} ASGs", total);

                overridesMap = asgSnapshot;
                overridesSubject.onNext(overridesMap);
            } catch (Exception e) {
                logger.error("failed updating ASG data", e);
            }
        }

        /**
         * Checks if the load balancer addition is disabled or not.
         *
         * @param asg the ASG object for which the status needs to be checked
         * @return true, if the load balancer addition is suspended, false otherwise.
         */
        private boolean isAddToLoadBalancerSuspended(AutoScalingGroup asg) {
            List<SuspendedProcess> suspendedProcesses = asg.getSuspendedProcesses();
            for (SuspendedProcess process : suspendedProcesses) {
                if (PROP_ADD_TO_LOAD_BALANCER.equals(process.getProcessName())) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A structure to hold both asgName and accountId, for querying asg data in multiple ec2 accounts
     */
    static class AsgAndAccount {
        final String asgName;
        final String accountId;

        AsgAndAccount(String asgName, String accountId) {
            this.asgName = asgName;
            this.accountId = accountId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AsgAndAccount)) return false;

            AsgAndAccount that = (AsgAndAccount) o;

            if (accountId != null ? !accountId.equals(that.accountId) : that.accountId != null) return false;
            if (asgName != null ? !asgName.equals(that.asgName) : that.asgName != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = asgName != null ? asgName.hashCode() : 0;
            result = 31 * result + (accountId != null ? accountId.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "AsgAndAccount{" +
                    "asgName='" + asgName + '\'' +
                    ", accountId='" + accountId + '\'' +
                    '}';
        }
    }
}

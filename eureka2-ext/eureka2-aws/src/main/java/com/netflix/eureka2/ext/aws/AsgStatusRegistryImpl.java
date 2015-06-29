package com.netflix.eureka2.ext.aws;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.SuspendedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

/**
 * {@link AsgStatusRegistryImpl} reads statuses of request ASGs from AWS, and refreshes their values
 * at a regular interval. The refresh and subscribe operations are executed on a common I/O scheduler, and
 * are serialized.
 *
 * @author Tomasz Bak
 */
@Singleton
public class AsgStatusRegistryImpl implements AsgStatusRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AsgStatusRegistryImpl.class);

    /* Visible for testing */ static final String PROP_ADD_TO_LOAD_BALANCER = "AddToLoadBalancer";

    private final AmazonAutoScaling amazonAutoScaling;
    private final AwsConfiguration configuration;

    private final Map<String, Subject<Boolean, Boolean>> asgUpdateSubjectsByName = new HashMap<>();

    private final Worker worker;
    private final RefreshTask refreshTask;

    private volatile Subscription refreshSubscription;

    @Inject
    public AsgStatusRegistryImpl(AmazonAutoScaling amazonAutoScaling, AwsConfiguration configuration) {
        this(amazonAutoScaling, configuration, Schedulers.io());
    }

    public AsgStatusRegistryImpl(AmazonAutoScaling amazonAutoScaling, AwsConfiguration configuration, Scheduler scheduler) {
        this.amazonAutoScaling = amazonAutoScaling;
        this.configuration = configuration;
        this.worker = scheduler.createWorker();
        this.refreshTask = new RefreshTask();
    }

    @PostConstruct
    public void start() {
        if (refreshSubscription != null) {
            throw new IllegalStateException("Already started");
        }
        scheduleRefresh();
    }

    @PreDestroy
    public void stop() {
        if (refreshSubscription != null) {
            refreshSubscription.unsubscribe();
            worker.unsubscribe();
        }
    }

    @Override
    public Observable<Boolean> asgStatusUpdates(final String asg) {
        return Observable.create(new OnSubscribe<Boolean>() {
            @Override
            public void call(final Subscriber<? super Boolean> subscriber) {
                if (worker.isUnsubscribed()) {
                    subscriber.unsubscribe();
                } else {
                    worker.schedule(new Action0() {
                        @Override
                        public void call() {
                            Subject<Boolean, Boolean> updateSubject = asgUpdateSubjectsByName.get(asg);
                            if (updateSubject == null) {
                                updateSubject = BehaviorSubject.create();
                                updateSubject.onNext(true); // Set default prior to first AWS call
                                asgUpdateSubjectsByName.put(asg, updateSubject);
                            }
                            updateSubject.subscribe(subscriber);
                        }
                    });
                }
            }
        });
    }

    /**
     * Provided for testing.
     */
    int getRegistrySize() {
        return asgUpdateSubjectsByName.size();
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
                doCleanup();
                doRefresh();
            } catch (Throwable e) {
                logger.error("ASG status refresh failed", e);
            }
            scheduleRefresh();
        }

        /**
         * Remove ASG update subjects with no subscribers.
         */
        private void doCleanup() {
            for (Entry<String, Subject<Boolean, Boolean>> entry : asgUpdateSubjectsByName.entrySet()) {
                if (!entry.getValue().hasObservers()) {
                    asgUpdateSubjectsByName.remove(entry.getKey());
                }
            }
        }

        /**
         * Read new ASG status updates from AWS.
         */
        private void doRefresh() {
            List<String> asgSnapshot = new ArrayList<>(asgUpdateSubjectsByName.keySet());
            if (asgSnapshot.isEmpty()) {
                return;
            }
            int total = 0;
            String nextToken = null;
            do {
                DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
                request.setMaxRecords(PAGE_SIZE);
                request.setNextToken(nextToken);
                request.setAutoScalingGroupNames(asgSnapshot.subList(total, Math.min(PAGE_SIZE, asgSnapshot.size())));
                DescribeAutoScalingGroupsResult result = amazonAutoScaling.describeAutoScalingGroups(request);

                for (AutoScalingGroup asg : result.getAutoScalingGroups()) {
                    Subject<Boolean, Boolean> updateSubject = asgUpdateSubjectsByName.get(asg.getAutoScalingGroupName());
                    if (updateSubject != null) {
                        updateSubject.onNext(isAddToLoadBalancerSuspended(asg));
                    }
                }
                nextToken = result.getNextToken();
                total += result.getAutoScalingGroups().size();
            } while (nextToken != null);
            logger.debug("Retrieved {} ASGs", total);
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
}

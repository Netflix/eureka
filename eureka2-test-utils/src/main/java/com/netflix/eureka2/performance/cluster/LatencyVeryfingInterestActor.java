package com.netflix.eureka2.performance.cluster;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.AsyncSubject;
import rx.subjects.Subject;

/**
 * @author Tomasz Bak
 */
public class LatencyVeryfingInterestActor extends ClientActor {

    private static final Logger logger = LoggerFactory.getLogger(LatencyVeryfingInterestActor.class);

    private static final AtomicInteger INSTANCE_IDX = new AtomicInteger();

    private final Random random = new Random(System.currentTimeMillis());
    private final Subscription interestSubscription;
    private final EurekaInterestClient interestClient;
    private final PerformanceScoreBoard scoreBoard;
    private final Subscription expectedNotificationsSubscriptions;
    private final AtomicBoolean connectedFlag = new AtomicBoolean(true);
    private final NotificationTracker notificationTracker;

    private final Subject<Void, Void> lifecycleSubject = AsyncSubject.create();

    public LatencyVeryfingInterestActor(final EurekaInterestClient interestClient,
                                        final Interest<InstanceInfo> interest,
                                        Observable<ChangeNotification<InstanceInfo>> expectedNotifications,
                                        int interestDuration,
                                        int latencyThreshold,
                                        PerformanceScoreBoard scoreBoard,
                                        Scheduler scheduler) {
        super("interestActor#" + INSTANCE_IDX.incrementAndGet());
        this.interestClient = interestClient;
        this.scoreBoard = scoreBoard;
        this.notificationTracker = new NotificationTracker(latencyThreshold, scoreBoard, scheduler);

        logger.info("Interest client");

        scoreBoard.subscribingActorIncrement();
        interestSubscription = interestClient
                .forInterest(interest)
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        disconnect();
                        logger.info("Terminating interest client");
                    }

                    @Override
                    public void onError(Throwable e) {
                        disconnect();
                        logger.error("Client interest failed", e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        if (notification.isDataNotification() && interest.matches(notification.getData())) {
                            notificationTracker.verifyWithExpectations(notification);
                            logger.debug("Notification={}", notification);
                        }
                    }
                });
        expectedNotificationsSubscriptions = expectedNotifications
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                              @Override
                              public void call(ChangeNotification<InstanceInfo> notification) {
                                  if (interest.matches(notification.getData())) {
                                      notificationTracker.addExpectation(notification);
                                  }
                              }
                          }
                )
                .subscribe();
        final Worker worker = scheduler.createWorker();
        scheduler.createWorker().schedule(new Action0() {
            @Override
            public void call() {
                worker.unsubscribe();
                disconnect();
            }
        }, random.nextInt(interestDuration), TimeUnit.MILLISECONDS);
    }

    @Override
    protected Observable<Void> lifecycle() {
        return lifecycleSubject;
    }

    @Override
    public void stop() {
        disconnect();
    }

    private void disconnect() {
        if (connectedFlag.compareAndSet(true, false)) {
            try {
                interestSubscription.unsubscribe();
                interestClient.shutdown();
                expectedNotificationsSubscriptions.unsubscribe();
                notificationTracker.stop();
            } finally {
                scoreBoard.subscribingActorDecrement();
                lifecycleSubject.onCompleted();
            }
        }
    }
}

package com.netflix.eureka2.performance;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.data.toplogy.TopologyFunctions;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.subjects.AsyncSubject;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * @author Tomasz Bak
 */
public class RegisteringActor extends ClientActor {

    private static final Logger logger = LoggerFactory.getLogger(RegisteringActor.class);

    private static final AtomicInteger INSTANCE_IDX = new AtomicInteger();

    private final Random random = new Random(System.currentTimeMillis());

    private final EurekaRegistrationClient registrationClient;
    private final int registrationDuration;
    private final InstanceInfo selfInfo;
    private final PerformanceScoreBoard scoreBoard;
    private final Scheduler scheduler;
    private final PublishSubject<ChangeNotification<InstanceInfo>> registrationPublishSubject = PublishSubject.create();

    private Worker worker;
    private final Subject<Void, Void> lifecycleSubject = AsyncSubject.create();

    private Subscription registrationSubscription;
    private Subscription firstRegistrationSubscription;

    public RegisteringActor(EurekaRegistrationClient registrationClient,
                            int registrationDuration,
                            final InstanceInfo selfInfo,
                            PerformanceScoreBoard scoreBoard,
                            final Scheduler scheduler) {
        super("registeringActor#" + INSTANCE_IDX.incrementAndGet());
        this.registrationClient = registrationClient;
        this.registrationDuration = registrationDuration;
        this.selfInfo = selfInfo;
        this.scoreBoard = scoreBoard;
        this.scheduler = scheduler;
    }

    public void start() {
        final long startTime = scheduler.now();

        final InstanceInfo taggedInstanceInfo = new InstanceInfo.Builder()
                .withInstanceInfo(selfInfo)
                .withMetaData(TopologyFunctions.TIME_STAMP_KEY, Long.toString(startTime))
                .build();

        final String id = this.selfInfo.getId();
        logger.info("Registering client {}", id);

        final AtomicBoolean connectedFlag = new AtomicBoolean(true);

        scoreBoard.registeringActorIncrement();
        registrationPublishSubject.onNext(new ChangeNotification<>(Kind.Add, this.selfInfo));
        RegistrationObservable registrationObservable = registrationClient.register(Observable.just(this.selfInfo));
        firstRegistrationSubscription = registrationObservable.initialRegistrationResult()
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Registration of instance {} succeeded", id);
                    }

                    @Override
                    public void onError(Throwable e) {
                        disconnect(connectedFlag);
                        logger.error("Registration of instance " + id + " failed", e);
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No-op
                    }
                });
        registrationSubscription = registrationObservable.subscribe(
                new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Unregistered instance {} after {}", id, scheduler.now() - startTime);
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Registration observable for instance {} onError-ed", id, scheduler.now() - startTime);
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No-op
                    }
                }
        );
        worker = scheduler.createWorker();
        // We do not want this be too short
        int finalDuration = random.nextInt(registrationDuration / 2) + registrationDuration / 2;
        scheduler.createWorker().schedule(new Action0() {
            @Override
            public void call() {
                worker.unsubscribe();
                if (firstRegistrationSubscription.isUnsubscribed()) { // == registration completed
                    registrationPublishSubject.onNext(new ChangeNotification<>(Kind.Delete, taggedInstanceInfo));
                    disconnect(connectedFlag);
                } else {
                    logger.info("Disconnecting not completed registration for instance {}", id);
                    firstRegistrationSubscription.unsubscribe();
                    disconnect(connectedFlag);
                }
            }
        }, finalDuration, TimeUnit.MILLISECONDS);
    }

    @Override
    protected Observable<Void> lifecycle() {
        return lifecycleSubject;
    }

    @Override
    public void stop() {
        if (worker != null) {
            worker.unsubscribe();
        }
        if (registrationClient != null) {
            registrationClient.shutdown();
        }
    }

    public Observable<ChangeNotification<InstanceInfo>> registrationNotifications() {
        return registrationPublishSubject;
    }

    private void disconnect(AtomicBoolean connectedFlag) {
        if (connectedFlag.compareAndSet(true, false)) {
            try {
                registrationPublishSubject.onCompleted();
                registrationSubscription.unsubscribe(); // does unregister if needed
                registrationPublishSubject.onCompleted();
                registrationClient.shutdown();
            } finally {
                scoreBoard.registeringActorDecrement();
                lifecycleSubject.onCompleted();
            }
        }
    }
}

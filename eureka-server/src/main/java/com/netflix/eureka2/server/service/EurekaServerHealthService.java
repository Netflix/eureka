package com.netflix.eureka2.server.service;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.InstanceInfoFromConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observers.SerializedSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author David Liu
 */
public abstract class EurekaServerHealthService implements SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServerHealthService.class);

    private static final int HEALTHCHECK_INTERVAL_SECONDS = 60;

    private final EurekaServerConfig config;
    private final int healthCheckIntervalSeconds;
    private final AtomicBoolean connected = new AtomicBoolean();
    private final ReplaySubject<InstanceInfo> replaySubject = ReplaySubject.create();

    private final AtomicReference<InstanceInfo.Status> lastSentStatusRef;
    private final Scheduler defaultServerHealthSchduler;
    private final Subscriber<InstanceInfo> serverHealthSubscriber;

    public EurekaServerHealthService(EurekaServerConfig config) {
        this(config, HEALTHCHECK_INTERVAL_SECONDS);
    }

    public EurekaServerHealthService(EurekaServerConfig config, int healthCheckIntervalSeconds) {
        this.config = config;
        this.lastSentStatusRef = new AtomicReference<>();
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
        this.serverHealthSubscriber = new SerializedSubscriber<>(new ServerHealthReporter());
        this.defaultServerHealthSchduler = Schedulers.computation();
    }

    /* visible for testing */ EurekaServerHealthService(EurekaServerConfig config,
                                                        int healthCheckIntervalSeconds,
                                                        Scheduler scheduler) {
        this.config = config;
        this.lastSentStatusRef = new AtomicReference<>();
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
        this.serverHealthSubscriber = new SerializedSubscriber<>(new ServerHealthReporter());
        this.defaultServerHealthSchduler = scheduler;
    }

    public void init() {
        resolve()
                .switchMap(new Func1<InstanceInfo, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(InstanceInfo instanceInfo) {
                        InstanceInfo starting = instanceInfo.toBuilder().withStatus(InstanceInfo.Status.STARTING).build();
                        logger.info("Attempting initial registration with instanceInfo {}", starting);

                        return report(starting);
                    }
                })
                .retry(3)  // TODO better retry?
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Initial registration completed");
                        initHealthCheck();
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("Initial registration failed after retries", e);
                    }

                    @Override
                    public void onNext(Void aVoid) {
                    }
                });
    }

    protected void initHealthCheck() {
        resolveServerHealth().flatMap(new Func1<InstanceInfo.Status, Observable<InstanceInfo>>() {
            @Override
            public Observable<InstanceInfo> call(final InstanceInfo.Status status) {
                return resolve().map(new Func1<InstanceInfo, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(InstanceInfo instanceInfo) {
                        return instanceInfo.toBuilder().withStatus(status).build();
                    }
                });
            }
        }).subscribe(serverHealthSubscriber);
    }

    public void shutdown() {
        serverHealthSubscriber.unsubscribe();
    }

    /**
     * Override to implement server specific deep health checks
     * @return a stream of aggregated overall health status for the server
     */
    public Observable<InstanceInfo.Status> resolveServerHealth() {
        return Observable.interval(healthCheckIntervalSeconds, TimeUnit.SECONDS, defaultServerHealthSchduler)
                .map(new Func1<Long, InstanceInfo.Status>() {
                    @Override
                    public InstanceInfo.Status call(Long aLong) {
                        return InstanceInfo.Status.UP;
                    }
                });
    }

    @Override
    public Observable<InstanceInfo> resolve() {
        if (connected.compareAndSet(false, true)) {
            return connect();
        }
        return replaySubject;
    }

    protected Observable<InstanceInfo> connect() {
        return new InstanceInfoFromConfig(config)
                .get()
                .map(resolveServersFunc())
                .map(new Func1<InstanceInfo.Builder, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(InstanceInfo.Builder builder) {
                        return builder.build();
                    }
                })
                .take(1)
                .doOnEach(new Action1<Notification<? super InstanceInfo>>() {
                    @Override
                    public void call(Notification<? super InstanceInfo> notification) {
                        switch (notification.getKind()) {
                            case OnNext:
                                replaySubject.onNext((InstanceInfo) notification.getValue());
                                replaySubject.onCompleted();
                                logger.info("Own instance info resolved to {}", notification.getValue());
                                break;
                            case OnError:
                                replaySubject.onError(notification.getThrowable());
                                logger.error("Could not resolve own instance info", notification.getThrowable());
                                break;
                        }
                    }
                });
    }

    protected abstract Func1<InstanceInfo.Builder, InstanceInfo.Builder> resolveServersFunc();

    public abstract Observable<Void> report(InstanceInfo instanceInfo);


    class ServerHealthReporter extends Subscriber<InstanceInfo> {
        @Override
        public void onCompleted() {
            logger.info("ServerHealthReport onCompleted");
        }

        @Override
        public void onError(Throwable e) {
            logger.warn("ServerHealthReport onError", e);
        }

        @Override
        public void onNext(final InstanceInfo instanceInfo) {
            final InstanceInfo.Status lastSendStatus = lastSentStatusRef.get();
            if (instanceInfo.getStatus() != lastSendStatus) {
                report(instanceInfo).subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        lastSentStatusRef.set(instanceInfo.getStatus());
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.warn("failed to update server status with new instanceInfo {}", instanceInfo, e);
                    }

                    @Override
                    public void onNext(Void aVoid) {

                    }
                });
            } else {
                logger.debug("status unchanged, not reporting");
            }
        }
    }

}

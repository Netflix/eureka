package com.netflix.eureka2.registry;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * @author Tomasz Bak
 */
public class PreservableRegistryProcessor implements EurekaRegistrationProcessor<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(PreservableRegistryProcessor.class);

    private final EurekaRegistrationProcessor<InstanceInfo> delegate;

    private final QuotaSubscriber quotaSubscriber;
    private final Subscription quotaSubscription;

    private final AtomicBoolean isShutdown = new AtomicBoolean();

    public PreservableRegistryProcessor(EurekaRegistrationProcessor<InstanceInfo> delegate,
                                        Observable<Long> evictionQuotas,
                                        EurekaRegistryMetricFactory metricFactory) {
        this.delegate = delegate;
        this.quotaSubscriber = new QuotaSubscriber();
        this.quotaSubscription = evictionQuotas.subscribe(quotaSubscriber);
    }

    @Override
    public Observable<Void> register(final String id, final Observable<InstanceInfo> registrationUpdates, final Source source) {
        return Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(final Subscriber<? super Void> clientSubscriber) {
                final PublishSubject<InstanceInfo> delegateUpdates = PublishSubject.create();
                delegate.register(id, delegateUpdates, source).subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        clientSubscriber.onCompleted();
                    }

                    @Override
                    public void onError(Throwable e) {
                        clientSubscriber.onError(e);
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No-op
                    }
                });

                registrationUpdates.materialize().subscribe(
                        new Action1<Notification<InstanceInfo>>() {
                            @Override
                            public void call(Notification<InstanceInfo> notification) {
                                switch (notification.getKind()) {
                                    case OnNext:
                                        delegateUpdates.onNext(notification.getValue());
                                        break;
                                    case OnCompleted:
                                        delegateUpdates.onCompleted();
                                        break;
                                    case OnError:
                                        clientSubscriber.onError(notification.getThrowable());
                                        quotaSubscriber.addToEvictionQueue(delegateUpdates);
                                }
                            }
                        }
                );

                clientSubscriber.onCompleted();
            }
        });
    }

    @Override
    public Observable<Boolean> register(InstanceInfo instanceInfo, Source source) {
        throw new IllegalStateException("method not implemented");
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo instanceInfo, Source source) {
        throw new IllegalStateException("method not implemented");
    }

    @Override
    public Observable<Void> shutdown() {
        _shutdown();
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        _shutdown();
        return Observable.error(cause);
    }

    private boolean _shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            quotaSubscription.unsubscribe();
            return true;
        }
        return false;
    }

    static class QuotaSubscriber extends Subscriber<Long> {

        private final Queue<Subject<InstanceInfo, InstanceInfo>> registrationsToEvict = new ConcurrentLinkedDeque<>();

        void addToEvictionQueue(Subject<InstanceInfo, InstanceInfo> subject) {
            registrationsToEvict.add(subject);
            request(1);
        }

        @Override
        public void onStart() {
            request(0);
        }

        @Override
        public void onCompleted() {
            logger.info("Eviction quota subscription onCompleted");
        }

        @Override
        public void onError(Throwable e) {
            logger.error("Eviction quota subscription terminated with an error", e);
        }

        @Override
        public void onNext(Long quota) {
            for (int i = 0; i < quota; i++) {
                Subject<InstanceInfo, InstanceInfo> subject = registrationsToEvict.poll();
                if (subject != null) {
                    subject.onCompleted();
                } else {
                    break;
                }
            }
        }
    }
}

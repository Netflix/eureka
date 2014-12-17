package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RetryableServiceChannel;
import com.netflix.eureka2.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func0;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author David Liu
 */
public class RetryableRegistrationChannel
        extends RetryableServiceChannel<RegistrationChannel>
        implements RegistrationChannel {

    private static final Logger logger = LoggerFactory.getLogger(RetryableRegistrationChannel.class);

    private final AtomicReference<InstanceInfo> instanceInfoRef;
    private final Func0<RegistrationChannel> channelFactory;

    public RetryableRegistrationChannel(Func0<RegistrationChannel> channelFactory, long retryInitialDelayMs, Scheduler scheduler) {
        super(channelFactory.call(), retryInitialDelayMs, scheduler);
        this.instanceInfoRef = new AtomicReference<>(null);
        this.channelFactory = channelFactory;
    }


    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return currentDelegateChannel().register(instanceInfo).doOnCompleted(new Action0() {
            @Override
            public void call() {
                instanceInfoRef.set(instanceInfo);
            }
        });
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        return currentDelegateChannel().update(newInfo).doOnCompleted(new Action0() {
            @Override
            public void call() {
                instanceInfoRef.set(newInfo);
            }
        });
    }

    @Override
    public Observable<Void> unregister() {
        return currentDelegateChannel().unregister().doOnCompleted(new Action0() {
            @Override
            public void call() {
                instanceInfoRef.set(null);
            }
        });
    }

    @Override
    protected Observable<RegistrationChannel> reestablish() {
        return Observable.create(new Observable.OnSubscribe<RegistrationChannel>() {
            @Override
            public void call(final Subscriber<? super RegistrationChannel> subscriber) {
                try {
                    final RegistrationChannel newDelegateChannel = channelFactory.call();
                    InstanceInfo instanceInfo = instanceInfoRef.get();

                    if (instanceInfo != null) {
                        newDelegateChannel.register(instanceInfo).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                logger.info("Retry re-registration completed");
                                subscriber.onNext(newDelegateChannel);
                                subscriber.onCompleted();
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.info("Retry re-registration failed");
                                subscriber.onError(e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                                // no op
                            }
                        });
                    }
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    @Override
    protected void _close() {
        super._close();
        instanceInfoRef.set(null);
    }
}

package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RetryableServiceChannel;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Func0;
import rx.functions.Func1;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Note that operations (register and unregister) on this registartion channel must be serialized by an external
 * guarantee (such as using a {@link com.netflix.eureka2.client.channel.RegistrationChannelInvoker}).
 *
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
        return currentDelegateChannelObservable().switchMap(new Func1<RegistrationChannel, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(RegistrationChannel registrationChannel) {
                // eagerly set the stateful reference as we would want to do it regardless of the success or failure
                // of the actual registration
                instanceInfoRef.set(instanceInfo);
                return registrationChannel.register(instanceInfo);
            }
        });
    }

    @Override
    public Observable<Void> unregister() {
        return currentDelegateChannelObservable().switchMap(new Func1<RegistrationChannel, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(RegistrationChannel registrationChannel) {
                // eagerly set the stateful reference as we would want to do it regardless of the success or failure
                // of the actual unregistration
                instanceInfoRef.set(null);
                return registrationChannel.unregister();
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
                    } else {
                        logger.info("InstanceInfo is null, no need to re-register");
                        subscriber.onNext(newDelegateChannel);
                        subscriber.onCompleted();
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

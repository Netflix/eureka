package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RegistrationChannel.STATE;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.RegistrationMessage;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Note that
 *
 * @author David Liu
 */
public class RegistrationChannelImpl extends AbstractHandlerChannel<STATE> implements RegistrationChannel, Sourced {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationChannelImpl.class);

    private final Observable<Void> control;
    private final Observable<ChangeNotification<InstanceInfo>> data;
    private final Subscriber<Void> controlSubscriber;
    private final Subscriber<Void> channelSubscriber;

    private volatile Source selfSource;
    private volatile InstanceInfo cachedInstanceInfo;

    public RegistrationChannelImpl(final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor,
                                   MessageConnection transport,
                                   RegistrationChannelMetrics metrics) {
        super(STATE.Idle, transport, metrics);
        this.channelSubscriber = new LoggingSubscriber<>(logger, "channel");
        this.controlSubscriber = new LoggingSubscriber<>(logger, "control");

        Observable<RegistrationMessage> input = connectInputToLifecycle(transport.incoming())
                .filter(new Func1<Object, Boolean>() {
                    @Override
                    public Boolean call(Object message) {
                        boolean isKnown = message instanceof RegistrationMessage;
                        if (!isKnown) {
                            logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
                        }
                        return isKnown;
                    }
                })
                .cast(RegistrationMessage.class)
                .doOnNext(new Action1<RegistrationMessage>() {
                    @Override
                    public void call(RegistrationMessage message) {
                        sendAckOnTransport().subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.warn("Failed to send ack for register operation for instanceInfo {}", cachedInstanceInfo);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });
                    }
                })
                .replay(1)
                .refCount();

        data = input
                .concatMap(new Func1<RegistrationMessage, Observable<? extends ChangeNotification<InstanceInfo>>>() {  // TODO switchable to map instead?
                    @Override
                    public Observable<? extends ChangeNotification<InstanceInfo>> call(RegistrationMessage message) {
                        if (STATE.Closed == state.get()) {
                            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
                        }

                        STATE currState = state.get();
                        switch (currState) {
                            case Idle:
                                if (message instanceof Register) {  // first time registration
                                    moveToState(STATE.Idle, STATE.Registered);
                                    cachedInstanceInfo = ((Register) message).getInstanceInfo();
                                    return Observable.just(new ChangeNotification<>(ChangeNotification.Kind.Add, cachedInstanceInfo));
                                } else { // can only be Unregister, and we've never registered in the first place
                                    // this code should not be reachable as the control would have terminated before now, here for safeguarding
                                    return Observable.<ChangeNotification<InstanceInfo>>empty()
                                            .doOnCompleted(new Action0() {
                                                @Override
                                                public void call() {
                                                    close();
                                                }
                                            });
                                }
                            case Registered:
                                if (message instanceof Register) {  // this is an update
                                    cachedInstanceInfo = ((Register) message).getInstanceInfo();
                                    return Observable.just(new ChangeNotification<>(ChangeNotification.Kind.Add, cachedInstanceInfo));
                                } else { // can only be Unregister
                                    return Observable.just(new ChangeNotification<>(ChangeNotification.Kind.Delete, cachedInstanceInfo))
                                            .doOnCompleted(new Action0() {
                                                @Override
                                                public void call() {
                                                    close();
                                                }
                                            });
                                }
                            case Closed:
                            default:
                                return Observable.<ChangeNotification<InstanceInfo>>empty()
                                        .doOnCompleted(new Action0() {
                                            @Override
                                            public void call() {
                                                close();
                                            }
                                        });
                        }
                    }
                });

        control = input
                .take(1)
                .doOnNext(new Action1<RegistrationMessage>() {
                    @Override
                    public void call(RegistrationMessage message) {
                        if (message instanceof Register) {
                            String instanceId = ((Register) message).getInstanceInfo().getId();
                            selfSource = new Source(Source.Origin.LOCAL, instanceId);
                            registrationProcessor.connect(instanceId, selfSource, data).subscribe(channelSubscriber);
                        } else {
                            String errorMsg = "Illegal initial registration message, should start with Register but was " + message;
                            logger.warn(errorMsg);
                            close(new IllegalArgumentException(errorMsg));
                        }
                    }
                })
                .ignoreElements()
                .cast(Void.class);

        start();
    }

    private void start() {
        control.subscribe(controlSubscriber);
    }

    @Override
    public Source getSource() {
        return selfSource;
    }

    @Override
    protected void _close() {
        STATE from = moveToState(STATE.Closed);
        if (from != STATE.Closed) {  // if this is the first/only close
            controlSubscriber.unsubscribe();
            channelSubscriber.unsubscribe();
            cachedInstanceInfo = null;
            super._close();
        }
    }


    // FIXME interface need to change
    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return null;
    }

    // FIXME interface need to change
    @Override
    public Observable<Void> unregister() {
        return null;
    }
}

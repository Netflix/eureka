package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RegistrationChannel.STATE;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.utils.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractHandlerChannel<STATE> implements RegistrationChannel, Sourced {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationChannelImpl.class);
    private static final Exception CONNECTION_TERMINATED = ExceptionUtils.trimStackTraceof(new Exception("Registration connection terminated without unregister request"));

    private final Source selfSource;
    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor;

    private final BehaviorSubject<InstanceInfo> registrationSubject = BehaviorSubject.create();

    public RegistrationChannelImpl(EurekaRegistrationProcessor registrationProcessor,
                                   final EvictionQueue evictionQueue,
                                   MessageConnection transport,
                                   RegistrationChannelMetrics metrics) {
        super(STATE.Idle, transport, metrics);
        this.registrationProcessor = registrationProcessor;

        selfSource = new Source(Source.Origin.LOCAL);

        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                if (message instanceof Register) {
                    InstanceInfo instanceInfo = ((Register) message).getInstanceInfo();
                    register(instanceInfo).subscribe(new Subscriber<Void>() {
                        @Override
                        public void onCompleted() {
                        }

                        @Override
                        public void onError(Throwable e) {
                            logger.warn("Error calling register", e);
                        }

                        @Override
                        public void onNext(Void aVoid) {
                        }
                    });
                } else if (message instanceof Unregister) {
                    unregister().subscribe(new Subscriber<Void>() {
                        @Override
                        public void onCompleted() {
                        }

                        @Override
                        public void onError(Throwable e) {
                            logger.warn("Error calling unregister", e);
                        }

                        @Override
                        public void onNext(Void aVoid) {
                        }
                    });
                } else {
                    sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
                }
            }
        });

        transport.lifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                evictIfPresent();
            }

            @Override
            public void onError(Throwable e) {
                evictIfPresent();
            }

            @Override
            public void onNext(Void aVoid) {
                // No op
            }

            private void evictIfPresent() {
                InstanceInfo toEvict = registrationSubject.getValue();
                if (toEvict != null) {
                    logger.info("Connection terminated without unregister; adding instance {} to eviction queue", toEvict.getId());
                    registrationSubject.onError(CONNECTION_TERMINATED);
                }
            }
        });
    }

    @Override
    public Source getSource() {
        return selfSource;
    }

    /**
     * Cases:
     * 1. channel state is Idle. Call register on the registry and
     *   1a. if successful, ack on the channel. If the ack fails, we ignore the failure and let the client deal with it
     *   2b. if unsuccessful, send error on the transport and close the channel (client need to reconnect back)
     * 2. channel state is Registered. This is the same as case 1.
     * 3. channel state is Closed. send ChannelClosedException on the transport and re-close the channel.
     *
     */
    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (moveToState(STATE.Idle, STATE.Registered)) {
            return firstRegistration(instanceInfo);
        }
        if (getState() == STATE.Registered) {
            return registrationUpdate(instanceInfo);
        }
        STATE currentState = getState();
        if (STATE.Closed == currentState) {
            // Since channel is already closed and hence the transport, we don't need to send an error on transport.
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        } else {
            Exception exception = new IllegalStateException("Unknown state error when registering, current state: " + currentState);
            return sendErrorOnTransport(exception).doOnTerminate(new Action0() {
                @Override
                public void call() {
                    close();
                }
            });
        }
    }

    private Observable<Void> firstRegistration(InstanceInfo instanceInfo) {
        logger.debug("Registering service in registry: {}", instanceInfo);
        registrationSubject.onNext(instanceInfo);

        return registrationProcessor.register(instanceInfo.getId(), registrationSubject, selfSource)
                .ignoreElements()
                .cast(Void.class)
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        sendErrorOnTransport(throwable).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                close();
                            }

                            @Override
                            public void onError(Throwable e) {
                                close(e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        sendAckOnTransport().subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.warn("Failed to send ack for register operation for instanceInfo {}", registrationSubject.getValue().getId());
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });
                    }
                });
    }

    private Observable<Void> registrationUpdate(InstanceInfo instanceInfo) {
        logger.debug("Registration update: {}", instanceInfo);
        registrationSubject.onNext(instanceInfo);
        return Observable.empty();
    }

    /**
     * Cases:
     * 1. channel state is Registered. Call unregister on the registry, and
     *   1a. if successful, ack on the channel and close the channel regardless of the ack result
     *   1b. if unsuccessful, send error on the transport. TODO should we optimize and close the channel here?
     * 2. channel state is Idle. This is a no-op so just ack on transport and close the channel
     * 3. channel state is Closed. This is a no-op so just ack on transport and close the channel
     *
     * Note that acks can fail often if the client walks away immediately after sending an unregister
     *
     */
    @Override
    public Observable<Void> unregister() {
        STATE currentState = moveToState(STATE.Closed);
        switch (currentState) {
            case Registered:
                InstanceInfo instanceInfo = registrationSubject.getValue();
                logger.info("Unregistering service in registry: {}", instanceInfo.getId());
                registrationSubject.onCompleted();
                break;
            case Closed:
                logger.info("Unregister on an already closed channel. This is a no-op");
                return Observable.empty();  // no need to send ack on transport as channel is already closed
            case Idle:
                logger.info("Unregistered an Idle channel, This is a no-op");
                break;
        }
        return sendAckOnTransport().doOnTerminate(new Action0() {
            @Override
            public void call() {
                close();
            }
        });
    }

    @Override
    protected void _close() {
        STATE previousState = moveToState(STATE.Closed);
        if (previousState != STATE.Closed) {
            super._close();
        }
    }
}

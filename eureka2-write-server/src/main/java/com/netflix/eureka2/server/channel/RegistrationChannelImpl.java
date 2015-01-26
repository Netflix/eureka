package com.netflix.eureka2.server.channel;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RegistrationChannel.STATE;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractHandlerChannel<STATE> implements RegistrationChannel, Sourced {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationChannelImpl.class);

    private final Source selfSource;
    private final AtomicReference<InstanceInfo> instanceInfoRef;

    public RegistrationChannelImpl(SourcedEurekaRegistry registry,
                                   final EvictionQueue evictionQueue,
                                   MessageConnection transport,
                                   RegistrationChannelMetrics metrics) {
        super(STATE.Idle, transport, registry, metrics);

        selfSource = new Source(Source.Origin.LOCAL);
        instanceInfoRef = new AtomicReference<>();

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
                InstanceInfo toEvict = instanceInfoRef.get();
                if (toEvict != null) {
                    logger.info("Connection terminated without unregister; adding instance {} to eviction queue", toEvict);
                    evictionQueue.add(toEvict, selfSource);
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
        if (!moveToState(STATE.Idle, STATE.Registered) &&
                !moveToState(STATE.Registered, STATE.Registered)) {
            STATE currentState = state.get();
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

        logger.debug("Registering service in registry: {}", instanceInfo);

        // it doesn't matter too much whether this cached instance info is the most update to date one
        instanceInfoRef.set(instanceInfo);

        return registry.register(instanceInfo, selfSource)
                .ignoreElements()
                .cast(Void.class)
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        sendErrorOnTransport(throwable).doOnTerminate(new Action0() {
                            @Override
                            public void call() {
                                close();
                            }
                        });
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        sendAckOnTransport().doOnError(new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                logger.warn("Failed to send ack for register operation for instanceInfo {}", instanceInfo);
                            }
                        });
                    }
                });
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
        STATE currentState = state.getAndSet(STATE.Closed);

        logger.debug("Unregistering service in registry: {}", instanceInfoRef.get());

        switch (currentState) {
            case Registered:
                InstanceInfo toUnregister = instanceInfoRef.get();

                return registry.unregister(toUnregister, selfSource)
                        .ignoreElements()
                        .cast(Void.class)
                        .doOnError(new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {
                                sendErrorOnTransport(throwable);
                            }
                        })
                        .doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                instanceInfoRef.set(null);
                                sendAckOnTransport().doOnTerminate(new Action0() {
                                    @Override
                                    public void call() {
                                        close();
                                    }
                                }).subscribe();
                            }
                        });
            case Closed:
                logger.info("Unregister on an already closed channel. This is a no-op");
                return Observable.empty();  // no need to send ack on transport as channel is already closed
            case Idle:
                logger.info("Unregistered an Idle channel, This is a no-op");
            default:
                return sendAckOnTransport().doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        close();
                    }
                });
        }
    }

    @Override
    protected void _close() {
        if (state.get() != STATE.Closed) {
            moveToState(state.get(), STATE.Closed);
        }
        super._close();
    }
}

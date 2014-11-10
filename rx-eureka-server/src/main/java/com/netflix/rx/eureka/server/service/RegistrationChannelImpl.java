package com.netflix.rx.eureka.server.service;

import java.util.Set;

import com.netflix.rx.eureka.protocol.EurekaProtocolError;
import com.netflix.rx.eureka.protocol.Heartbeat;
import com.netflix.rx.eureka.protocol.registration.Register;
import com.netflix.rx.eureka.protocol.registration.Unregister;
import com.netflix.rx.eureka.protocol.registration.Update;
import com.netflix.rx.eureka.registry.Delta;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.server.transport.ClientConnection;
import com.netflix.rx.eureka.service.RegistrationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 * @author Nitesh Kant
 */
public class RegistrationChannelImpl extends AbstractChannel<RegistrationChannelImpl.STATES> implements RegistrationChannel {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationChannelImpl.class);

    private static final IllegalStateException INSTANCE_ALREADY_REGISTERED_EXCEPTION =
            new IllegalStateException("An instance is already registered. You must update instance instead.");

    private static final IllegalStateException INSTANCE_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("Instance is not registered yet.");

    private final RegistrationChannelMetrics metrics;

    private volatile InstanceInfo currentInfo;
    private volatile long currentVersion;

    protected enum STATES {Idle, Registered, Closed}

    public RegistrationChannelImpl(EurekaRegistry registry, ClientConnection transport, RegistrationChannelMetrics metrics) {
        super(STATES.Idle, transport, registry, 3, 30000);
        this.metrics = metrics;

        metrics.incrementStateCounter(STATES.Idle);

        currentVersion = System.currentTimeMillis();
        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                if (message instanceof Register) {
                    InstanceInfo instanceInfo = ((Register) message).getInstanceInfo();
                    register(instanceInfo);// No need to subscribe, the register() call does the subscription.
                } else if (message instanceof Unregister) {
                    unregister();// No need to subscribe, the unregister() call does the subscription.
                } else if (message instanceof Update) {
                    InstanceInfo instanceInfo = ((Update) message).getInstanceInfo();
                    update(instanceInfo);// No need to subscribe, the update() call does the subscription.
                } else if (message instanceof Heartbeat) {
                    heartbeat();
                } else {
                    sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
                }
            }
        });
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        logger.debug("Registering service in registry: {}", instanceInfo);

        if (!moveToState(STATES.Idle, STATES.Registered)) {// State check. Only register if the state is Idle.
            if (STATES.Closed == state.get()) {
                /**
                 * Since channel is already closed and hence the transport, we don't need to send an error on transport.
                 */
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            } else {
                sendErrorOnTransport(INSTANCE_ALREADY_REGISTERED_EXCEPTION);
                return Observable.error(INSTANCE_ALREADY_REGISTERED_EXCEPTION);
            }
        }

        final long tempNewVersion = currentVersion + 1;
        final InstanceInfo tempNewInfo = new InstanceInfo.Builder()
                .withInstanceInfo(instanceInfo).withVersion(tempNewVersion).build();

        Observable<Void> registerResult = registry.register(tempNewInfo);
        registerResult.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                currentVersion = tempNewVersion;
                currentInfo = tempNewInfo;
                sendAckOnTransport();
            }

            @Override
            public void onError(Throwable e) {
                sendErrorOnTransport(e);
                moveToState(STATES.Registered, STATES.Idle); // Set the state back to enable subsequent
                // registrations.
            }

            @Override
            public void onNext(Void aVoid) {
                // Nothing to do for a void.
            }
        }); // Callers aren't required to subscribe, so it is eagerly subscribed.
        return registerResult;
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        logger.debug("Updating service entry in registry. New info= {}", newInfo);

        STATES currentState = state.get();
        switch (currentState) {
            case Idle:
                return Observable.error(INSTANCE_NOT_REGISTERED_EXCEPTION);
            case Registered:
                final long tempNewVersion = currentVersion + 1;
                final InstanceInfo tempNewInfo = new InstanceInfo.Builder()
                        .withInstanceInfo(newInfo).withVersion(tempNewVersion).build();

                Set<Delta<?>> deltas = tempNewInfo.diffOlder(currentInfo);
                logger.debug("Set of InstanceInfo modified fields: {}", deltas);

                // TODO: shall we chain ack observable with update?
                Observable<Void> updateResult = registry.update(tempNewInfo, deltas);
                updateResult.subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        currentVersion = tempNewVersion;
                        currentInfo = tempNewInfo;
                        sendAckOnTransport();
                    }

                    @Override
                    public void onError(Throwable e) {
                        sendErrorOnTransport(e);
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No op
                    }
                });
                return updateResult;
            case Closed:
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            default:
                return Observable.error(new IllegalStateException("Unrecognized channel state: " + currentState));
        }
    }

    @Override
    public Observable<Void> unregister() {
        if (!moveToState(STATES.Registered, STATES.Closed)) {
            STATES currentState = state.get();
            if (currentState == STATES.Idle) {
                return Observable.error(INSTANCE_NOT_REGISTERED_EXCEPTION);
            }
            if (currentState == STATES.Closed) {
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            }
            return Observable.error(new IllegalStateException("Unrecognized channel state: " + currentState));
        }

        Observable<Void> updateResult = registry.unregister(currentInfo.getId());
        updateResult.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                currentInfo = null;
                sendAckOnTransport();
            }

            @Override
            public void onError(Throwable e) {
                sendErrorOnTransport(e);
            }

            @Override
            public void onNext(Void aVoid) {
                // No op
            }
        });

        return updateResult;

    }

    protected boolean moveToState(STATES from, STATES to) {
        if (state.compareAndSet(from, to)) {
            metrics.decrementStateCounter(from);
            metrics.incrementStateCounter(to);
            return true;
        }
        return false;
    }
}

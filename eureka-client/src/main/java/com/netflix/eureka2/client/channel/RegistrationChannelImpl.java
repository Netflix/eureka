package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.client.transport.TransportClient;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.protocol.registration.Update;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.service.RegistrationChannel;
import com.netflix.eureka2.transport.MessageConnection;
import rx.Observable;
import rx.functions.Func1;

/**
 * An implementation of {@link RegistrationChannel}. It is mandatory that all operations
 * on the channel are serialized, by the external client. This class is not thread safe and all operations on it
 * shall be executed by the same thread.
 *
 * Use {@link InterestChannelInvoker} for serializing operations on this channel.
 *
 * @author Nitesh Kant
 */
/*pkg-private: Used by EurekaClientService only*/class RegistrationChannelImpl
        extends AbstractChannel<RegistrationChannelImpl.STATES> implements RegistrationChannel {

    private static final IllegalStateException INSTANCE_ALREADY_REGISTERED_EXCEPTION =
            new IllegalStateException("An instance is already registered. You must update instance instead.");

    private static final IllegalStateException INSTANCE_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("Instance is not registered yet.");

    protected enum STATES {Idle, Registered, Closed}

    private final RegistrationChannelMetrics metrics;

    public RegistrationChannelImpl(TransportClient transportClient, RegistrationChannelMetrics metrics) {
        super(STATES.Idle, transportClient);
        this.metrics = metrics;
        metrics.incrementStateCounter(STATES.Idle);
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (!moveToState(STATES.Idle, STATES.Registered)) {// State check. Only register if the state is Idle.
            STATES currentState = state.get();
            switch (currentState) {
                case Registered:
                    return Observable.error(INSTANCE_ALREADY_REGISTERED_EXCEPTION);
                case Closed:
                    return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            }
        }

        //TODO: Need to serialize register -> update -> unregister. With this code both they can be interleaved
        return connect().switchMap(new Func1<MessageConnection, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(MessageConnection connection) {
                return connection.submitWithAck(new Register(instanceInfo));
            }
        });
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        STATES currentState = state.get();
        switch (currentState) {
            case Idle:
                return Observable.error(INSTANCE_NOT_REGISTERED_EXCEPTION);
            case Registered:
                //TODO: Need to serialize register -> update -> unregister. With this code both they can be interleaved
                return connect().switchMap(new Func1<MessageConnection, Observable<? extends Void>>() {
                    @Override
                    public Observable<? extends Void> call(MessageConnection connection) {
                        return connection.submitWithAck(new Update(newInfo));
                    }
                });
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
        //TODO: Need to serialize register -> update -> unregister. With this code both they can be interleaved
        return connect().switchMap(new Func1<MessageConnection, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(MessageConnection connection) {
                return connection.submitWithAck(Unregister.INSTANCE);
            }
        });
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

package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.ServerConnection;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.RegistrationChannel;
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

    public RegistrationChannelImpl(TransportClient transportClient) {
        super(STATES.Idle, transportClient, 30000);
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (!state.compareAndSet(STATES.Idle, STATES.Registered)) {// State check. Only register if the state is Idle.
            STATES currentState = state.get();
            switch (currentState) {
                case Registered:
                    return Observable.error(INSTANCE_ALREADY_REGISTERED_EXCEPTION);
                case Closed:
                    return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            }
        }

        //TODO: Need to serialize register -> update -> unregister. With this code both they can be interleaved
        return connect().switchMap(new Func1<ServerConnection, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(ServerConnection connection) {
                return connection.send(new Register(instanceInfo));
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
                return connect().switchMap(new Func1<ServerConnection, Observable<? extends Void>>() {
                    @Override
                    public Observable<? extends Void> call(ServerConnection connection) {
                        return connection.send(new Update(newInfo));
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
        STATES currentState = state.get();
        switch (currentState) {
            case Idle:
                return Observable.error(INSTANCE_NOT_REGISTERED_EXCEPTION);
            case Registered:
                //TODO: Need to serialize register -> update -> unregister. With this code both they can be interleaved
                return connect().switchMap(new Func1<ServerConnection, Observable<? extends Void>>() {
                    @Override
                    public Observable<? extends Void> call(ServerConnection connection) {
                        return connection.send(new Unregister());
                    }
                });
            case Closed:
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            default:
                return Observable.error(new IllegalStateException("Unrecognized channel state: " + currentState));
        }
    }
}

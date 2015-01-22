package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.AbstractClientChannel;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.channel.RegistrationChannel.STATE;
import com.netflix.eureka2.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
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
public class RegistrationChannelImpl extends AbstractClientChannel<STATE> implements RegistrationChannel {

    private static final IllegalStateException INSTANCE_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("Instance is not registered yet.");

    private final RegistrationChannelMetrics metrics;

    public RegistrationChannelImpl(TransportClient transportClient, RegistrationChannelMetrics metrics) {
        super(STATE.Idle, transportClient, metrics);
        this.metrics = metrics;
        metrics.incrementStateCounter(STATE.Idle);
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (!moveToState(STATE.Idle, STATE.Registered) &&
                !moveToState(STATE.Registered, STATE.Registered)) {
            STATE currentState = state.get();
            if (currentState == STATE.Closed) {
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            } else {
                return Observable.error(new IllegalStateException(
                        "Error advancing to state Registered from state " + currentState));
            }
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(MessageConnection connection) {
                return connection.submitWithAck(new Register(instanceInfo));
            }
        });
    }

    @Override
    public Observable<Void> unregister() {
        if (!moveToState(STATE.Registered, STATE.Closed)) {
            STATE currentState = state.get();
            if (currentState == STATE.Idle) {
                return Observable.error(INSTANCE_NOT_REGISTERED_EXCEPTION);
            } else if (currentState == STATE.Closed) {
                return Observable.error(CHANNEL_CLOSED_EXCEPTION);
            } else {
                return Observable.error(new IllegalStateException(
                        "Error advancing to state Closed from state " + currentState));
            }
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(final MessageConnection connection) {
                return connection.submitWithAck(Unregister.INSTANCE);
                // we can optimize here by closing the connection when this onComplete, but the registrationHandler
                // also does this for us so let's not over optimize.
            }
        });
    }

    @Override
    protected void _close() {
        if (state.get() != STATE.Closed) {
            moveToState(state.get(), STATE.Closed);
        }
        super._close();
    }
}

package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.AbstractClientChannel;
import com.netflix.eureka2.client.channel.RegistrationChannelImpl.STATES;
import com.netflix.eureka2.client.metric.RegistrationChannelMetrics;
import com.netflix.eureka2.transport.TransportClient;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RegistrationChannelImpl
        extends AbstractClientChannel<STATES> implements RegistrationChannel {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationChannelImpl.class);

    private static final IllegalStateException INSTANCE_NOT_REGISTERED_EXCEPTION =
            new IllegalStateException("Instance is not registered yet.");

    public enum STATES {Idle, Registered, Closed}

    private final RegistrationChannelMetrics metrics;

    public RegistrationChannelImpl(TransportClient transportClient, RegistrationChannelMetrics metrics) {
        super(STATES.Idle, transportClient);
        this.metrics = metrics;
        metrics.incrementStateCounter(STATES.Idle);
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (!moveToState(STATES.Idle, STATES.Registered) &&
            !moveToState(STATES.Registered, STATES.Registered)) {
            STATES currentState = state.get();
            if (currentState == STATES.Closed) {
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
        if (!moveToState(STATES.Registered, STATES.Closed)) {
            STATES currentState = state.get();
            if (currentState == STATES.Idle) {
                return Observable.error(INSTANCE_NOT_REGISTERED_EXCEPTION);
            } else if (currentState == STATES.Closed) {
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

    protected boolean moveToState(STATES from, STATES to) {
        if (state.compareAndSet(from, to)) {
            if (from != to) {
                metrics.decrementStateCounter(from);
                metrics.incrementStateCounter(to);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void _close() {
        if (state.get() != STATES.Closed) {
            moveToState(state.get(), STATES.Closed);
        }
        super._close();
    }
}

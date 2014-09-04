package com.netflix.eureka.server.transport;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class ClientConnectionImpl implements ClientConnection {

    private final MessageBroker broker;

    public ClientConnectionImpl(MessageBroker broker) {
        this.broker = broker;
    }

    @Override
    public Observable<Object> getInput() {
        return broker.incoming();
    }

    @Override
    public Observable<Void> sendNotification(ChangeNotification<InstanceInfo> notification) {
        switch (notification.getKind()) {
            case Add:
                return broker.submitWithAck(new AddInstance(notification.getData()));
            case Delete:
                return broker.submitWithAck(new DeleteInstance(notification.getData().getId()));
            case Modify:
                ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) notification;

                /**
                 * Below will only work correctly if {@link MessageBroker#submitWithAck(Object)} is a lazy submit i.e.
                 * the message is only sent over the wire when subscribed. If it is eager i.e. the message is written
                 * to the underlying connection without subscription then {@link Observable#concatWith(Observable)}
                 * will eagerly write all the messages without waiting for an ack.
                 */
                Observable<Void> toReturn = null;
                for (final Delta<?> delta : modifyNotification.getDelta()) {
                    if (null == toReturn) {
                        toReturn = broker.submitWithAck(new UpdateInstanceInfo(delta));
                    } else {
                        toReturn.concatWith(broker.submitWithAck(new UpdateInstanceInfo(delta)));
                    }
                }
                return toReturn;
        }
        return Observable.error(new IllegalArgumentException("Unknown change notification type: " +
                                                             notification.getKind()));
    }

    @Override
    public void shutdown() {
        broker.shutdown();
    }

    @Override
    public Observable<Void> sendAcknowledgment() {
        return broker.submit(Acknowledgement.INSTANCE);
    }

    @Override
    public Observable<Void> sendError(Throwable error) {
        // TODO: Auto-generated method stub
        return Observable.empty();
    }

    @Override
    public Observable<Void> sendOnComplete() {
        // TODO: Auto-generated method stub
        return Observable.empty();
    }
}

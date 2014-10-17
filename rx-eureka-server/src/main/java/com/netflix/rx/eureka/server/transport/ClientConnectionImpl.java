package com.netflix.rx.eureka.server.transport;

import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.ModifyNotification;
import com.netflix.rx.eureka.protocol.discovery.AddInstance;
import com.netflix.rx.eureka.protocol.discovery.DeleteInstance;
import com.netflix.rx.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.rx.eureka.registry.Delta;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.transport.Acknowledgement;
import com.netflix.rx.eureka.transport.MessageBroker;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * @author Nitesh Kant
 */
public class ClientConnectionImpl implements ClientConnection {

    private final MessageBroker broker;
    private final long startTime;
    private final ClientConnectionMetrics metrics;

    public ClientConnectionImpl(MessageBroker broker, ClientConnectionMetrics metrics) {
        this.broker = broker;
        this.metrics = metrics;
        this.startTime = System.currentTimeMillis();
        metrics.incrementConnectedClients();
    }

    @Override
    public Observable<Object> getInput() {
        return broker.incoming().doOnNext(new Action1<Object>() {
            @Override
            public void call(Object o) {
                metrics.incrementIncomingMessageCounter(o.getClass(), 1);
            }
        });
    }

    @Override
    public Observable<Void> sendNotification(ChangeNotification<InstanceInfo> notification) {
        switch (notification.getKind()) {
            case Add:
                metrics.incrementOutgoingMessageCounter(AddInstance.class, 1);
                return broker.submitWithAck(new AddInstance(notification.getData()));
            case Delete:
                metrics.incrementOutgoingMessageCounter(DeleteInstance.class, 1);
                return broker.submitWithAck(new DeleteInstance(notification.getData().getId()));
            case Modify:
                final ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) notification;

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
                metrics.incrementOutgoingMessageCounter(UpdateInstanceInfo.class, modifyNotification.getDelta().size());
                toReturn.doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        metrics.incrementIncomingMessageCounter(Acknowledgement.class, modifyNotification.getDelta().size());
                    }
                });
                return toReturn;
        }
        return Observable.error(new IllegalArgumentException("Unknown change notification type: " +
                                                             notification.getKind()));
    }

    @Override
    public void shutdown() {
        metrics.decrementConnectedClients();
        metrics.clientConnectionTime(startTime);
        broker.shutdown();
    }

    @Override
    public Observable<Void> sendAcknowledgment() {
        metrics.incrementOutgoingMessageCounter(Acknowledgement.class, 1);
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

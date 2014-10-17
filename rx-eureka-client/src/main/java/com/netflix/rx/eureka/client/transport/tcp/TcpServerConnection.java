package com.netflix.rx.eureka.client.transport.tcp;

import com.netflix.rx.eureka.client.transport.ServerConnection;
import com.netflix.rx.eureka.client.transport.ServerConnectionMetrics;
import com.netflix.rx.eureka.protocol.Heartbeat;
import com.netflix.rx.eureka.transport.Acknowledgement;
import com.netflix.rx.eureka.transport.MessageBroker;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * @author Nitesh Kant
 */
public class TcpServerConnection implements ServerConnection {

    private final MessageBroker broker;
    private final ServerConnectionMetrics metrics;
    private final long startTime;

    public TcpServerConnection(MessageBroker broker, ServerConnectionMetrics metrics) {
        this.broker = broker;
        this.metrics = metrics;
        metrics.incrementClientConnections();
        this.startTime = System.currentTimeMillis();
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
    public void shutdown() {
        metrics.clientConnectionTime(startTime);
        metrics.decrementClientConnections();
        broker.shutdown();
    }

    @Override
    public Observable<Void> send(Object message) {
        metrics.incrementOutgoingMessageCounter(message.getClass(), 1);
        return broker.submit(message);
    }

    @Override
    public Observable<Void> sendWithAck(Object message) {
        metrics.incrementOutgoingMessageCounter(message.getClass(), 1);
        final long startTime = System.currentTimeMillis();
        return broker.submitWithAck(message).doOnCompleted(new Action0() {
            @Override
            public void call() {
                metrics.incrementIncomingMessageCounter(Acknowledgement.class, 1);
                metrics.ackWaitTime(startTime);
            }
        });
    }

    @Override
    public Observable<Void> sendHeartbeat() {
        metrics.incrementOutgoingMessageCounter(Heartbeat.class, 1);
        return broker.submit(Heartbeat.INSTANCE);
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
    public void close() {
        broker.shutdown();
    }
}

package com.netflix.rx.eureka.client.transport.tcp;

import com.netflix.rx.eureka.client.transport.ServerConnection;
import com.netflix.rx.eureka.protocol.Heartbeat;
import com.netflix.rx.eureka.transport.Acknowledgement;
import com.netflix.rx.eureka.transport.MessageBroker;
import rx.Observable;

/**
 * @author Nitesh Kant
 */
public class TcpServerConnection implements ServerConnection {

    private final MessageBroker broker;

    public TcpServerConnection(MessageBroker broker) {
        this.broker = broker;
    }

    @Override
    public Observable<Object> getInput() {
        return broker.incoming();
    }

    @Override
    public void shutdown() {
        broker.shutdown();
    }

    @Override
    public Observable<Void> send(Object message) {
        return broker.submit(message);
    }

    @Override
    public Observable<Void> sendWithAck(Object message) {
        return broker.submitWithAck(message);
    }

    @Override
    public Observable<Void> sendHeartbeat() {
        return broker.submit(Heartbeat.INSTANCE);
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
    public void close() {
        broker.shutdown();

    }
}

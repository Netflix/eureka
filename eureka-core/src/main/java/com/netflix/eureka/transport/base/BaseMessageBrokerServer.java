package com.netflix.eureka.transport.base;

import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class BaseMessageBrokerServer implements MessageBrokerServer {
    // Package private for testing purposes.
    final RxServer<Message, Message> server;
    private final PublishSubject<MessageBroker> brokersSubject;

    public BaseMessageBrokerServer(RxServer<Message, Message> server, PublishSubject<MessageBroker> brokersSubject) {
        this.server = server;
        this.brokersSubject = brokersSubject;
    }

    @Override
    public BaseMessageBrokerServer start() {
        server.start();
        return this;
    }

    @Override
    public Observable<MessageBroker> clientConnections() {
        return brokersSubject;
    }

    @Override
    public int getServerPort() {
        return server.getServerPort();
    }

    @Override
    public void shutdown() throws InterruptedException {
        server.shutdown();
    }
}

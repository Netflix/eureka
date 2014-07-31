package com.netflix.eureka.transport;

import io.reactivex.netty.server.RxServer;
import rx.Observable;

/**
 * Represents server side, where for each new client connection we create distinct {@link MessageBroker}.
 * It is equivalent to {@link RxServer}.
 *
 * @author Tomasz Bak
 */
public interface MessageBrokerServer {

    Observable<MessageBroker> clientConnections();

    int getServerPort();

    MessageBrokerServer start();

    void shutdown() throws InterruptedException;
}

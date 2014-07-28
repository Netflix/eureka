package com.netflix.eureka.transport.avro;

import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
class AvroMessageBrokerServer implements MessageBrokerServer {
    // Package private for testing purposes.
    final RxServer<Message, Message> server;
    private final PublishSubject<MessageBroker> brokersSubject;

    AvroMessageBrokerServer(int port, PipelineConfigurator<Message, Message> pipelineConfigurator) {
        brokersSubject = PublishSubject.create();
        server = RxNetty.newTcpServerBuilder(port, new ConnectionHandler<Message, Message>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Message, Message> newConnection) {
                AvroMessageBroker broker = new AvroMessageBroker(newConnection);
                brokersSubject.onNext(broker);
                return broker.lifecycleObservable();
            }
        }).pipelineConfigurator(pipelineConfigurator).enableWireLogging(LogLevel.ERROR).build();
    }

    @Override
    public AvroMessageBrokerServer start() {
        server.start();
        return this;
    }

    @Override
    public Observable<MessageBroker> clientConnections() {
        return brokersSubject;
    }

    @Override
    public void shutdown() throws InterruptedException {
        server.shutdown();
    }
}

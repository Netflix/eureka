package com.netflix.eureka.transport;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.rx.RxBlocking;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.eureka.transport.TransportCompatibilityTestSuite.DiscoveryProtocolTest;
import com.netflix.eureka.transport.TransportCompatibilityTestSuite.RegistrationProtocolTest;
import com.netflix.eureka.transport.base.BaseMessageBroker;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.server.RxServer;
import org.junit.After;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * This is protocol compatibility test for any underlying transport we implement.
 *
 * @author Tomasz Bak
 */
public class EurekaTransportsTest {

    private RxServer<Object, Object> server;

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testRegistrationProtocolWithAvro() throws Exception {
        registrationProtocolTest(Codec.Avro);
    }

    @Test
    public void testRegistrationProtocolWithJson() throws Exception {
        registrationProtocolTest(Codec.Json);
    }

    @Test(timeout = 10000)
    public void testDiscoveryProtocolWithAvro() throws Exception {
        discoveryProtocolTest(Codec.Avro);
    }

    @Test
    public void testDiscoveryProtocolWithJson() throws Exception {
        discoveryProtocolTest(Codec.Json);
    }

    public void registrationProtocolTest(Codec codec) throws Exception {
        Iterator<MessageBroker> serverBrokerIterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, serverConnection(EurekaTransports.registrationPipeline(codec)));
        MessageBroker clientBroker = clientConnection(EurekaTransports.registrationPipeline(codec));
        MessageBroker serverBroker = serverBrokerIterator.next();

        new RegistrationProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    public void discoveryProtocolTest(Codec codec) throws Exception {
        Iterator<MessageBroker> serverBrokerIterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, serverConnection(EurekaTransports.discoveryPipeline(codec)));
        MessageBroker clientBroker = clientConnection(EurekaTransports.discoveryPipeline(codec));
        MessageBroker serverBroker = serverBrokerIterator.next();

        new DiscoveryProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    private Observable<MessageBroker> serverConnection(PipelineConfigurator<Object, Object> pipelineConfigurator) {
        final PublishSubject<MessageBroker> subject = PublishSubject.create();
        server = RxNetty.createTcpServer(0, pipelineConfigurator, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
                BaseMessageBroker messageBroker = new BaseMessageBroker(connection);
                subject.onNext(messageBroker);
                return messageBroker.lifecycleObservable();
            }
        }).start();
        return subject;
    }

    private MessageBroker clientConnection(PipelineConfigurator<Object, Object> pipelineConfigurator) {
        ObservableConnection<Object, Object> clientConnection = RxNetty.createTcpClient("localhost", server.getServerPort(), pipelineConfigurator)
                .connect().take(1).timeout(1, TimeUnit.SECONDS).toBlocking().single();
        return new BaseMessageBroker(clientConnection);
    }
}
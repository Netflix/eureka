package com.netflix.eureka2.transport;

import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import com.netflix.eureka2.transport.TransportCompatibilityTestSuite.DiscoveryProtocolTest;
import com.netflix.eureka2.transport.TransportCompatibilityTestSuite.RegistrationProtocolTest;
import com.netflix.eureka2.transport.TransportCompatibilityTestSuite.ReplicationProtocolTest;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.metric.MessageConnectionMetrics;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.server.RxServer;
import org.junit.After;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.transport.EurekaTransports.discoveryPipeline;
import static com.netflix.eureka2.transport.EurekaTransports.registrationPipeline;
import static com.netflix.eureka2.transport.EurekaTransports.replicationPipeline;

/**
 * This is protocol compatibility test for any underlying transport we implement.
 *
 * @author Tomasz Bak
 */
public class EurekaTransportsTest {

    private RxServer<Object, Object> server;

    private MessageConnectionMetrics clientMetrics = new MessageConnectionMetrics("client-compatibility");
    private MessageConnectionMetrics serverMetrics = new MessageConnectionMetrics("server-compatibility");

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test(timeout = 10000)
    public void testRegistrationProtocolWithAvro() throws Exception {
        registrationProtocolTest(Codec.Avro);
    }

    @Test(timeout = 10000)
    public void testRegistrationProtocolWithJson() throws Exception {
        registrationProtocolTest(Codec.Json);
    }

    @Test(timeout = 10000)
    public void testReplicationProtocolWithJson() throws Exception {
        replicationProtocolTest(Codec.Json);
    }

    @Test(timeout = 10000)
    public void testReplicationProtocolWithAvro() throws Exception {
        replicationProtocolTest(Codec.Avro);
    }

    @Test(timeout = 10000)
    public void testDiscoveryProtocolWithAvro() throws Exception {
        discoveryProtocolTest(Codec.Avro);
    }

    @Test(timeout = 10000)
    public void testDiscoveryProtocolWithJson() throws Exception {
        discoveryProtocolTest(Codec.Json);
    }

    public void registrationProtocolTest(Codec codec) throws Exception {
        Iterator<MessageConnection> serverBrokerIterator = RxBlocking.iteratorFrom(
                1, TimeUnit.SECONDS,
                serverConnection(registrationPipeline(codec))
        );
        MessageConnection clientBroker = clientConnection(registrationPipeline(codec));
        MessageConnection serverBroker = serverBrokerIterator.next();

        new RegistrationProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    public void replicationProtocolTest(Codec codec) throws Exception {
        Iterator<MessageConnection> serverBrokerIterator = RxBlocking.iteratorFrom(
                1, TimeUnit.SECONDS,
                serverConnection(replicationPipeline(codec))
        );
        MessageConnection clientBroker = clientConnection(replicationPipeline(codec));
        MessageConnection serverBroker = serverBrokerIterator.next();

        new ReplicationProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    public void discoveryProtocolTest(Codec codec) throws Exception {
        Iterator<MessageConnection> serverBrokerIterator = RxBlocking.iteratorFrom(
                1, TimeUnit.SECONDS,
                serverConnection(discoveryPipeline(codec))
        );
        MessageConnection clientBroker = clientConnection(discoveryPipeline(codec));
        MessageConnection serverBroker = serverBrokerIterator.next();

        new DiscoveryProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    private Observable<MessageConnection> serverConnection(PipelineConfigurator<Object, Object> pipelineConfigurator) {
        final PublishSubject<MessageConnection> subject = PublishSubject.create();
        server = RxNetty.createTcpServer(0, pipelineConfigurator, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
                BaseMessageConnection messageBroker = new BaseMessageConnection("test", connection, serverMetrics);
                subject.onNext(messageBroker);
                return messageBroker.lifecycleObservable();
            }
        }).start();
        return subject;
    }

    private MessageConnection clientConnection(PipelineConfigurator<Object, Object> pipelineConfigurator) {
        ObservableConnection<Object, Object> clientConnection = RxNetty.createTcpClient("localhost", server.getServerPort(), pipelineConfigurator)
                .connect().take(1).timeout(1, TimeUnit.SECONDS).toBlocking().single();
        return new BaseMessageConnection("test", clientConnection, clientMetrics);
    }
}
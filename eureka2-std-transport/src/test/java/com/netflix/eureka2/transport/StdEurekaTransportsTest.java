/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.transport;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.metric.MessageConnectionMetrics;
import com.netflix.eureka2.metric.noop.NoOpMessageConnectionMetrics;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.testkit.compatibility.transport.TransportCompatibilityTestSuite.DiscoveryProtocolTest;
import com.netflix.eureka2.testkit.compatibility.transport.TransportCompatibilityTestSuite.RegistrationProtocolTest;
import com.netflix.eureka2.testkit.compatibility.transport.TransportCompatibilityTestSuite.ReplicationProtocolTest;
import com.netflix.eureka2.testkit.internal.rx.RxBlocking;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import io.reactivex.netty.server.RxServer;
import org.junit.After;
import org.junit.Test;
import rx.Observable;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.transport.EurekaTransports.interestPipeline;
import static com.netflix.eureka2.transport.EurekaTransports.registrationPipeline;
import static com.netflix.eureka2.transport.EurekaTransports.replicationPipeline;

/**
 * This is protocol compatibility test for any underlying transport we implement.
 *
 * @author Tomasz Bak
 */
public class StdEurekaTransportsTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    private RxServer<Object, Object> server;

    private final MessageConnectionMetrics clientMetrics = NoOpMessageConnectionMetrics.INSTANCE;
    private final MessageConnectionMetrics serverMetrics = NoOpMessageConnectionMetrics.INSTANCE;

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Json to Json
     */

    @Test(timeout = 10000)
    public void testRegistrationProtocolWithJson() throws Exception {
        registrationProtocolTest();
    }

    @Test(timeout = 10000)
    public void testReplicationProtocolWithJson() throws Exception {
        replicationProtocolTest();
    }

    @Test
//    @Test(timeout = 10000)
    public void testDiscoveryProtocolWithJson() throws Exception {
        interestProtocolTest();
    }

    /**
     * Helpers
     */

    public void registrationProtocolTest() throws Exception {
        Iterator<EurekaConnection> serverBrokerIterator = RxBlocking.iteratorFrom(
                1, TimeUnit.SECONDS,
                serverConnection(registrationPipeline())
        );
        EurekaConnection clientBroker = clientConnection(registrationPipeline());
        EurekaConnection serverBroker = serverBrokerIterator.next();

        new RegistrationProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    public void replicationProtocolTest() throws Exception {
        Iterator<EurekaConnection> serverBrokerIterator = RxBlocking.iteratorFrom(
                1, TimeUnit.SECONDS,
                serverConnection(replicationPipeline())
        );
        EurekaConnection clientBroker = clientConnection(replicationPipeline());
        EurekaConnection serverBroker = serverBrokerIterator.next();

        new ReplicationProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    public void interestProtocolTest() throws Exception {
        Iterator<EurekaConnection> serverBrokerIterator = RxBlocking.iteratorFrom(
                1, TimeUnit.SECONDS,
                serverConnection(interestPipeline())
        );
        EurekaConnection clientBroker = clientConnection(interestPipeline());
        EurekaConnection serverBroker = serverBrokerIterator.next();

        new DiscoveryProtocolTest(
                clientBroker,
                serverBroker
        ).runTestSuite();
    }

    private Observable<EurekaConnection> serverConnection(PipelineConfigurator<Object, Object> pipelineConfigurator) {
        final PublishSubject<EurekaConnection> subject = PublishSubject.create();
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

    private EurekaConnection clientConnection(PipelineConfigurator<Object, Object> pipelineConfigurator) {
        ObservableConnection<Object, Object> clientConnection = RxNetty.createTcpClient("localhost", server.getServerPort(), pipelineConfigurator)
                .connect().take(1).timeout(1, TimeUnit.SECONDS).toBlocking().single();
        return new BaseMessageConnection("test", clientConnection, clientMetrics);
    }
}
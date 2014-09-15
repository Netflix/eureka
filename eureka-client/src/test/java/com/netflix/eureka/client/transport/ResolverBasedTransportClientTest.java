/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.client.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.ServerResolver.Protocol;
import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.client.transport.tcp.TcpRegistrationClient;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.rx.RxBlocking;
import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class ResolverBasedTransportClientTest {

    RxServer<Object, Object> server;

    @Before
    public void setUp() throws Exception {
        server = RxNetty.newTcpServerBuilder(0, new ConnectionHandler<Object, Object>() {
            @Override
            public Observable<Void> handle(final ObservableConnection<Object, Object> connection) {
                return connection.getInput().flatMap(new Func1<Object, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(Object o) {
                        return connection.writeAndFlush(Acknowledgement.INSTANCE);
                    }
                });
            }
        }).pipelineConfigurator(EurekaTransports.registrationPipeline(Codec.Json))
                .enableWireLogging(LogLevel.ERROR)
                .build()
                .start();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testRibbonLoadBalancer() throws Exception {
        StaticServerResolver<InetSocketAddress> resolver = new StaticServerResolver<>();
        resolver.addServer(new InetSocketAddress("localhost", 1), Protocol.TcpRegistration);

        ResolverBasedTransportClient<InetSocketAddress> transportClient = new TcpRegistrationClient(resolver, Codec.Json);

        // Single, non-existent server - should fail on it
        try {
            transportClient.connect().toBlocking().toFuture().get(30, TimeUnit.SECONDS);
            fail("Connection to server should have failed");
        } catch (Exception ex) {
            // As expected
        }

        // Now add our test server
        resolver.addServer(new InetSocketAddress("localhost", server.getServerPort()), Protocol.TcpRegistration);
        transportClient.loadBalancer.updateListOfServers(); // We do not want to wait for the background thread to refresh it.

        ServerConnection connection = transportClient.connect().toBlocking().toFuture().get(30, TimeUnit.SECONDS);
        assertNotNull("Connection not established", connection);
        Observable<Void> ackObservable = connection.send(new Register(SampleInstanceInfo.DiscoveryServer.build()));

        assertTrue("Acknowledgment not received in time", RxBlocking.isCompleted(30, TimeUnit.SECONDS, ackObservable));
    }
}
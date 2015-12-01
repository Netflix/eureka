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

package com.netflix.eureka2.ext.grpc.transport.server;

import com.netflix.eureka2.ext.grpc.model.transport.GrpcServerHelloWrapper;
import com.netflix.eureka2.grpc.Eureka2InterestGrpc;
import com.netflix.eureka2.grpc.Eureka2RegistrationGrpc;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.ServerHello;
import io.grpc.ServerBuilder;
import io.grpc.internal.ServerImpl;
import io.grpc.netty.NettyServer;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;

/**
 */
class GrpcEurekaServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcEurekaServer.class);

    private final ServerImpl server;
    private final int port;

    private final GrpcEureka2RegistrationServerImpl registrationService;
    private final GrpcEureka2InterestServerImpl interestService;

    GrpcEurekaServer(int port,
                     Source serverSource,
                     ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory,
                     ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory,
                     ChannelPipelineFactory<Object, Object> replicationAcceptor) throws IOException {

        ServerHello serverHello = GrpcServerHelloWrapper.newServerHello(serverSource);

        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(port);

        if (registrationPipelineFactory != null) {
            registrationService = new GrpcEureka2RegistrationServerImpl(serverHello, registrationPipelineFactory);
            serverBuilder.addService(Eureka2RegistrationGrpc.bindService(registrationService));
        } else {
            registrationService = null;
        }
        if (interestPipelineFactory != null) {
            interestService = new GrpcEureka2InterestServerImpl(
                    serverHello,
                    interestPipelineFactory
            );
            serverBuilder.addService(Eureka2InterestGrpc.bindService(interestService));
        } else {
            interestService = null;
        }
        server = (ServerImpl) serverBuilder.build().start();
        this.port = port == 0 ? getEphemeralPort(server) : port;
        logger.info("Started server on port {}", this.port);
    }

    public void shutdown() {
        if (!server.isShutdown()) {
            logger.info("Shutting down server on port {}", port);
            server.shutdown();
        }
    }

    public int getServerPort() {
        return port;
    }

    private static int getEphemeralPort(ServerImpl server) {
        Field transportServerField;
        try {
            transportServerField = ServerImpl.class.getDeclaredField("transportServer");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Internal implementation has changed. Cannot extract ephemeral port number", e);
        }
        transportServerField.setAccessible(true);

        NettyServer nettyServer;
        try {
            nettyServer = (NettyServer) transportServerField.get(server);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Access of internal Grpc server state failure", e);
        }

        Field channelField;
        try {
            channelField = NettyServer.class.getDeclaredField("channel");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Internal implementation has changed. Cannot extract ephemeral port number", e);
        }
        channelField.setAccessible(true);

        Channel channel;
        try {
            channel = (Channel) channelField.get(nettyServer);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Access of internal Grpc server state failure", e);
        }

        InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
        return socketAddress.getPort();
    }
}

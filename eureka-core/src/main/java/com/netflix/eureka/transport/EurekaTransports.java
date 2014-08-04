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

package com.netflix.eureka.transport;

import java.net.InetSocketAddress;

import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.base.TcpMessageBrokerBuilder;
import com.netflix.eureka.transport.codec.avro.AvroPipelineConfigurator;
import com.netflix.eureka.transport.codec.json.JsonPipelineConfigurator;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;

/**
 * Communication endpoint factory methods.
 *
 * @author Tomasz Bak
 */
public class EurekaTransports {

    public enum Codec {
        Avro,
        Json
    }

    static final Class<?>[] REGISTRATION_MODEL = {
            Register.class, Unregister.class, Heartbeat.class, Update.class
    };
    static final Class<?>[] DISCOVERY_MODEL = {
            RegisterInterestSet.class, UnregisterInterestSet.class, Heartbeat.class,
            AddInstance.class, DeleteInstance.class, UpdateInstanceInfo.class
    };

    public static Observable<MessageBroker> tcpRegistrationClient(String host, int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withCodecPipeline(piplineFor(codec, REGISTRATION_MODEL))
                .buildClient();
    }

    public static MessageBrokerServer tcpRegistrationServer(int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(port))
                .withCodecPipeline(piplineFor(codec, REGISTRATION_MODEL))
                .buildServer();
    }

    public static Observable<MessageBroker> tcpDiscoveryClient(String host, int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withCodecPipeline(piplineFor(codec, DISCOVERY_MODEL))
                .buildClient();
    }

    public static MessageBrokerServer tcpDiscoveryServer(int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(port))
                .withCodecPipeline(piplineFor(codec, DISCOVERY_MODEL))
                .buildServer();
    }

    public static Observable<MessageBroker> tcpRegistrationClient(String host, int port) {
        return tcpRegistrationClient(host, port, Codec.Avro);
    }

    public static MessageBrokerServer tcpRegistrationServer(int port) {
        return tcpRegistrationServer(port, Codec.Avro);
    }

    public static Observable<MessageBroker> tcpDiscoveryClient(String host, int port) {
        return tcpDiscoveryClient(host, port, Codec.Avro);
    }

    public static MessageBrokerServer tcpDiscoveryServer(int port) {
        return tcpDiscoveryServer(port, Codec.Avro);
    }

    static PipelineConfigurator<Message, Message> piplineFor(Codec codec, Class<?>[] model) {
        switch (codec) {
            case Avro:
                return new AvroPipelineConfigurator(model);
            case Json:
                return new JsonPipelineConfigurator();
        }
        throw new IllegalArgumentException("internal error - missing pipelinie implementation for codec " + codec);
    }
}
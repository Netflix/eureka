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

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka.interests.ApplicationInterest;
import com.netflix.eureka.interests.FullRegistryInterest;
import com.netflix.eureka.interests.InstanceInterest;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.VipsInterest;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfoTypes;
import com.netflix.eureka.transport.base.TcpMessageBrokerBuilder;
import com.netflix.eureka.transport.codec.avro.AvroPipelineConfigurator;
import com.netflix.eureka.transport.codec.json.JsonPipelineConfigurator;
import com.netflix.eureka.transport.utils.TransportModel;
import com.netflix.eureka.transport.utils.TransportModel.TransportModelBuilder;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import rx.Observable;

/**
 * Communication endpoint factory methods.
 *
 * @author Tomasz Bak
 */
public final class EurekaTransports {

    private EurekaTransports() {
    }

    public enum Codec {
        Avro,
        Json
    }

    static final Class<?>[] REGISTRATION_PROTOCOL_MODEL = {
            Register.class, Unregister.class, Heartbeat.class, Update.class
    };
    static final Class<?>[] DISCOVERY_PROTOCOL_MODEL = {
            RegisterInterestSet.class, UnregisterInterestSet.class, Heartbeat.class,
            AddInstance.class, DeleteInstance.class, UpdateInstanceInfo.class
    };

    public static final TransportModel REGISTRATION_MODEL = new TransportModelBuilder(REGISTRATION_PROTOCOL_MODEL).build();

    public static final TransportModel DISCOVERY_MODEL;

    static {
        TransportModelBuilder builder = new TransportModelBuilder(DISCOVERY_PROTOCOL_MODEL);
        builder.withHierarchy(Interest.class, VipsInterest.class, ApplicationInterest.class, InstanceInterest.class, FullRegistryInterest.class);
        Map<Type, Collection<Type>> valueTypeMap = new HashMap<Type, Collection<Type>>();
        for (TypeVariable typeVariable : Delta.class.getTypeParameters()) {
            valueTypeMap.put(typeVariable, InstanceInfoTypes.VALUE_TYPES);
        }
        builder.withFieldUnions(valueTypeMap);
        DISCOVERY_MODEL = builder.build();
    }

    public static Observable<MessageBroker> tcpRegistrationClient(String host, int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withCodecPiepline(piplineFor(codec, REGISTRATION_MODEL))
                .buildClient();
    }

    public static MessageBrokerServer tcpRegistrationServer(int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(port))
                .withCodecPiepline(piplineFor(codec, REGISTRATION_MODEL))
                .buildServer();
    }

    public static Observable<MessageBroker> tcpDiscoveryClient(String host, int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withCodecPiepline(piplineFor(codec, DISCOVERY_MODEL))
                .buildClient();
    }

    public static MessageBrokerServer tcpDiscoveryServer(int port, Codec codec) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(port))
                .withCodecPiepline(piplineFor(codec, DISCOVERY_MODEL))
                .buildServer();
    }

    static PipelineConfigurator<Object, Object> piplineFor(Codec codec, TransportModel model) {
        switch (codec) {
            case Avro:
                return new AvroPipelineConfigurator(model);
            case Json:
                return new JsonPipelineConfigurator(model);
        }
        throw new IllegalArgumentException("internal error - missing pipelinie implementation for codec " + codec);
    }
}
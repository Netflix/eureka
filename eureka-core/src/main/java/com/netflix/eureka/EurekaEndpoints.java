package com.netflix.eureka;

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
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.base.TcpMessageBrokerBuilder;
import com.netflix.eureka.transport.codec.avro.AvroPipelineConfigurator;
import rx.Observable;

/**
 * Communication endpoint factory methods.
 *
 * @author Tomasz Bak
 */
public class EurekaEndpoints {

    private static final Class<?>[] REGISTRATION_MODEL = {
            Register.class, Unregister.class, Heartbeat.class, Update.class
    };
    private static final Class<?>[] DISCOVERY_MODEL = {
            RegisterInterestSet.class, UnregisterInterestSet.class, Heartbeat.class,
            AddInstance.class, DeleteInstance.class, UpdateInstanceInfo.class
    };

    public static Observable<MessageBroker> tcpRegistrationClient(String host, int port) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withCodecPipeline(new AvroPipelineConfigurator(REGISTRATION_MODEL))
                .buildClient();
    }

    public static MessageBrokerServer tcpRegistrationServer(int port) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(port))
                .withCodecPipeline(new AvroPipelineConfigurator(REGISTRATION_MODEL))
                .buildServer();
    }

    public static Observable<MessageBroker> tcpDiscoveryClient(String host, int port) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withCodecPipeline(new AvroPipelineConfigurator(DISCOVERY_MODEL))
                .buildClient();
    }

    public static MessageBrokerServer tcpDiscoveryServer(int port) {
        return new TcpMessageBrokerBuilder(new InetSocketAddress(port))
                .withCodecPipeline(new AvroPipelineConfigurator(DISCOVERY_MODEL))
                .buildServer();
    }
}
package com.netflix.eureka;

import java.net.InetSocketAddress;

import com.netflix.eureka.protocol.discovery.NoInterest;
import com.netflix.eureka.protocol.discovery.UpdateInterestSet;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.avro.AvroMessageBrokerBuilder;
import rx.Observable;

/**
 * Communication endpoint factory methods.
 *
 * @author Tomasz Bak
 */
public class EurekaEndpoints {

    private static final Class<?>[] REGISTRATION_MODEL = {Register.class, Unregister.class, Update.class};
    private static final Class<?>[] DISCOVERY_MODEL = {UpdateInterestSet.class, NoInterest.class};

    public static Observable<MessageBroker> tcpRegistrationClient(String host, int port) {
        return new AvroMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withTypes(REGISTRATION_MODEL)
                .buildClient();
    }

    public static MessageBrokerServer tcpRegistrationServer(int port) {
        return new AvroMessageBrokerBuilder(new InetSocketAddress(port))
                .withTypes(REGISTRATION_MODEL)
                .buildServer();
    }

    public static Observable<MessageBroker> tcpDiscoveryClient(String host, int port) {
        return new AvroMessageBrokerBuilder(new InetSocketAddress(host, port))
                .withTypes(DISCOVERY_MODEL)
                .buildClient();
    }

    public static MessageBrokerServer tcpDiscoveryServer(int port) {
        return new AvroMessageBrokerBuilder(new InetSocketAddress(port))
                .withTypes(DISCOVERY_MODEL)
                .buildServer();
    }
}
package com.netflix.eureka.transport;

import com.netflix.eureka.transport.TransportCompatibilityTestSuite.DiscoveryProtocolTest;
import com.netflix.eureka.transport.TransportCompatibilityTestSuite.RegistrationProtocolTest;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.eureka.transport.utils.BrokerUtils.BrokerPair;
import org.junit.Test;
import rx.Observable;

/**
 * This is protocol compatibility test for any underlying transport we implement.
 *
 * @author Tomasz Bak
 */
public class EurekaTransportsTest {

    @Test
    public void testRegistrationProtocolWithAvro() throws Exception {
        registrationProtocolTest(Codec.Avro);
    }

    @Test
    public void testRegistrationProtocolWithJson() throws Exception {
        registrationProtocolTest(Codec.Json);
    }

    @Test
    public void testDiscoveryProtocolWithAvro() throws Exception {
        discoveryProtocolTest(Codec.Avro);
    }

    @Test
    public void testDiscoveryProtocolWithJson() throws Exception {
        discoveryProtocolTest(Codec.Json);
    }

    public void registrationProtocolTest(Codec codec) throws Exception {
        MessageBrokerServer server = EurekaTransports.tcpRegistrationServer(0, codec).start();
        try {
            Observable<MessageBroker> clientObservable = EurekaTransports.tcpRegistrationClient("localhost", server.getServerPort(), codec);
            BrokerPair brokerPair = new BrokerPair(server.clientConnections(), clientObservable);

            new RegistrationProtocolTest(brokerPair.getClientBroker(), brokerPair.getServerBroker()).runTestSuite();
        } finally {
            server.shutdown();
        }
    }

    public void discoveryProtocolTest(Codec codec) throws Exception {
        MessageBrokerServer server = EurekaTransports.tcpDiscoveryServer(0, codec).start();
        try {
            Observable<MessageBroker> clientObservable = EurekaTransports.tcpDiscoveryClient("localhost", server.getServerPort(), codec);
            BrokerPair brokerPair = new BrokerPair(server.clientConnections(), clientObservable);

            new DiscoveryProtocolTest(brokerPair.getClientBroker(), brokerPair.getServerBroker()).runTestSuite();
        } finally {
            server.shutdown();
        }
    }
}
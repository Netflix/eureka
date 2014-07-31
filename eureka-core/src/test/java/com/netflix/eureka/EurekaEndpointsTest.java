package com.netflix.eureka;

import com.netflix.eureka.TransportCompatibilityTestSuite.DiscoveryProtocolTest;
import com.netflix.eureka.TransportCompatibilityTestSuite.RegistrationProtocolTest;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.utils.BrokerUtils.BrokerPair;
import org.junit.Test;
import rx.Observable;

/**
 * This is protocol compatibility test for any underlying transport we implement.
 *
 * @author Tomasz Bak
 */
public class EurekaEndpointsTest {

    @Test
    public void testRegistrationProtocol() throws Exception {
        MessageBrokerServer server = EurekaEndpoints.tcpRegistrationServer(0).start();
        try {
            Observable<MessageBroker> clientObservable = EurekaEndpoints.tcpRegistrationClient("localhost", server.getServerPort());
            BrokerPair brokerPair = new BrokerPair(server.clientConnections(), clientObservable);

            new RegistrationProtocolTest(brokerPair.getClientBroker(), brokerPair.getServerBroker()).runTestSuite();
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void testDiscoveryProtocol() throws Exception {
        MessageBrokerServer server = EurekaEndpoints.tcpDiscoveryServer(0).start();
        try {
            Observable<MessageBroker> clientObservable = EurekaEndpoints.tcpDiscoveryClient("localhost", server.getServerPort());
            BrokerPair brokerPair = new BrokerPair(server.clientConnections(), clientObservable);

            new DiscoveryProtocolTest(brokerPair.getClientBroker(), brokerPair.getServerBroker()).runTestSuite();
        } finally {
            server.shutdown();
        }
    }
}
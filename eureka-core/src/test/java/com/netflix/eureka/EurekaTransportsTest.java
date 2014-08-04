package com.netflix.eureka;

import com.netflix.eureka.TransportCompatibilityTestSuite.DiscoveryProtocolTest;
import com.netflix.eureka.TransportCompatibilityTestSuite.RegistrationProtocolTest;
import com.netflix.eureka.transport.EurekaTransports;
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
public class EurekaTransportsTest {

    @Test
    public void testRegistrationProtocol() throws Exception {
        MessageBrokerServer server = EurekaTransports.tcpRegistrationServer(0).start();
        try {
            Observable<MessageBroker> clientObservable = EurekaTransports.tcpRegistrationClient("localhost", server.getServerPort());
            BrokerPair brokerPair = new BrokerPair(server.clientConnections(), clientObservable);

            new RegistrationProtocolTest(brokerPair.getClientBroker(), brokerPair.getServerBroker()).runTestSuite();
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void testDiscoveryProtocol() throws Exception {
        MessageBrokerServer server = EurekaTransports.tcpDiscoveryServer(0).start();
        try {
            Observable<MessageBroker> clientObservable = EurekaTransports.tcpDiscoveryClient("localhost", server.getServerPort());
            BrokerPair brokerPair = new BrokerPair(server.clientConnections(), clientObservable);

            new DiscoveryProtocolTest(brokerPair.getClientBroker(), brokerPair.getServerBroker()).runTestSuite();
        } finally {
            server.shutdown();
        }
    }
}
package com.netflix.eureka2.transport;

import com.netflix.eureka2.interests.SampleInterest;
import com.netflix.eureka2.protocol.Heartbeat;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.InterestRegistration;
import com.netflix.eureka2.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.protocol.registration.Register;
import com.netflix.eureka2.protocol.registration.Unregister;
import com.netflix.eureka2.protocol.registration.Update;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.protocol.replication.UpdateCopy;
import com.netflix.eureka2.registry.Delta.Builder;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.InstanceInfoField;
import com.netflix.eureka2.registry.SampleDelta;
import com.netflix.eureka2.registry.SampleServicePort;
import com.netflix.eureka2.registry.ServicePort;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.utils.Sets;
import rx.Notification;
import rx.Observable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.registry.SampleInstanceInfo.DiscoveryServer;
import static com.netflix.eureka2.rx.RxSniffer.sniff;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public abstract class TransportCompatibilityTestSuite {

    protected final MessageConnection clientBroker;
    protected final MessageConnection serverBroker;
    protected final Iterator<Object> serverIterator;
    protected final Iterator<Object> clientIterator;

    protected TransportCompatibilityTestSuite(MessageConnection clientBroker, MessageConnection serverBroker) {
        this.clientBroker = clientBroker;
        this.serverBroker = serverBroker;
        serverIterator = serverBroker.incoming().toBlocking().getIterator();
        clientIterator = clientBroker.incoming().toBlocking().getIterator();
    }

    public <T> void runClientToServer(T content) {
        sniff("submit", clientBroker.submit(content));
        T receivedMsg = (T) serverIterator.next();
        assertEquals(content, receivedMsg);
    }

    public <T> void runClientToServerWithAck(T content) {
        runWithAck(clientBroker, serverBroker, serverIterator, content);
    }

    public <T> void runServerToClientWithAck(T content) {
        runWithAck(serverBroker, clientBroker, clientIterator, content);
    }

    private <T> void runWithAck(MessageConnection source, MessageConnection dest, Iterator<Object> destIt, T content) {
        Observable<Void> ack = sniff("ack", source.submitWithAck(content));
        Iterator<Notification<Void>> ackIterator = ack.materialize().toBlocking().getIterator();

        T receivedMsg = (T) destIt.next();
        assertEquals(content, receivedMsg);

        RxBlocking.isCompleted(1000, TimeUnit.SECONDS, dest.acknowledge());

        assertTrue("Expected successful acknowledgement", ackIterator.next().isOnCompleted());
    }

    public static class RegistrationProtocolTest extends TransportCompatibilityTestSuite {

        public RegistrationProtocolTest(MessageConnection clientBroker, MessageConnection serverBroker) {
            super(clientBroker, serverBroker);
        }

        public void runTestSuite() {
            registrationTest();
            unregisterTest();
            updateTest();
            hearbeatTest();
        }

        private void registrationTest() {
            runClientToServerWithAck(new Register(DiscoveryServer.build()));
        }

        private void unregisterTest() {
            runClientToServerWithAck(new Unregister());
        }

        private void updateTest() {
            runClientToServerWithAck(new Update(DiscoveryServer.build()));
        }

        private void hearbeatTest() {
            runClientToServer(Heartbeat.INSTANCE);
        }
    }

    public static class ReplicationProtocolTest extends TransportCompatibilityTestSuite {

        private final InstanceInfo instanceInfo = DiscoveryServer.build();

        public ReplicationProtocolTest(MessageConnection clientBroker, MessageConnection serverBroker) {
            super(clientBroker, serverBroker);
        }

        public void runTestSuite() {
            handshakeTest();
            registrationTest();
            registrationWithNullsTest();
            unregisterTest();
            updateTest();
            hearbeatTest();
        }

        private void handshakeTest() {
            runClientToServerWithAck(new ReplicationHello("testId", 1));
            runClientToServerWithAck(new ReplicationHelloReply("testId", true));
        }

        private void registrationTest() {
            runClientToServerWithAck(new RegisterCopy(instanceInfo));
        }

        private void registrationWithNullsTest() {
            // Verify data cleanup
            HashSet<String> healthCheckUrls = new HashSet<>();
            healthCheckUrls.add(null);
            HashSet<ServicePort> ports = new HashSet<>();
            ports.add(null);

            InstanceInfo emptyInstanceInfo = new InstanceInfo.Builder()
                    .withId("id#empty")
                    .withPorts(ports)
                    .withHealthCheckUrls(healthCheckUrls)
                    .build();
            runClientToServerWithAck(new RegisterCopy(emptyInstanceInfo));
        }

        private void unregisterTest() {
            runClientToServerWithAck(new UnregisterCopy(instanceInfo.getId()));
        }

        private void updateTest() {
            runClientToServerWithAck(new UpdateCopy(instanceInfo));
        }

        private void hearbeatTest() {
            runClientToServer(Heartbeat.INSTANCE);
        }
    }

    public static class DiscoveryProtocolTest extends TransportCompatibilityTestSuite {
        public DiscoveryProtocolTest(MessageConnection clientBroker, MessageConnection serverBroker) {
            super(clientBroker, serverBroker);
        }

        public void runTestSuite() {
            // Client
            registerInterestSetTest();
            unregisterInterestSetTest();
            hearbeatTest();

            // Server
            addInstanceTest();
            deleteInstanceTest();
            updateInstanceInfoTest();
        }

        private void registerInterestSetTest() {
            runClientToServerWithAck(new InterestRegistration(SampleInterest.MultipleApps.build()));
        }

        private void unregisterInterestSetTest() {
            runClientToServerWithAck(UnregisterInterestSet.INSTANCE);
        }

        private void hearbeatTest() {
            runClientToServer(Heartbeat.INSTANCE);
        }

        private void addInstanceTest() {
            runServerToClientWithAck(new AddInstance(DiscoveryServer.build()));
        }

        private void deleteInstanceTest() {
            runServerToClientWithAck(new DeleteInstance("id1"));
        }

        private void updateInstanceInfoTest() {
            Builder builder = SampleDelta.Delta.builder();
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.APPLICATION_GROUP, "newAppGroup").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.APPLICATION, "newApplication").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.ASG, "newASG").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.VIP_ADDRESS, "newVipAddress").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.SECURE_VIP_ADDRESS, "newSecureVipAddress").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.PORTS, SampleServicePort.httpPorts()).build()));
            runServerToClientWithAck(new UpdateInstanceInfo(SampleDelta.StatusDown.build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.HOMEPAGE_URL, "newHomePageURL").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.STATUS_PAGE_URL, "newStatusPageURL").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.HEALTHCHECK_URLS, Sets.asSet("http://newHealthCheck1", "http://newHealthCheck2")).build()));

            // Update with null values (delete semantic)
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.APPLICATION, null).build()));
        }
    }
}
package com.netflix.eureka.transport;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.utils.Sets;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.InterestRegistration;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.Delta.Builder;
import com.netflix.eureka.registry.InstanceInfoField;
import com.netflix.eureka.registry.SampleDelta;
import com.netflix.eureka.interests.SampleInterest;
import com.netflix.eureka.rx.RxBlocking;
import rx.Notification;
import rx.Observable;

import static com.netflix.eureka.registry.SampleInstanceInfo.*;
import static com.netflix.eureka.rx.RxSniffer.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public abstract class TransportCompatibilityTestSuite {

    protected final MessageBroker clientBroker;
    protected final MessageBroker serverBroker;
    protected final Iterator<Object> serverIterator;
    protected final Iterator<Object> clientIterator;

    protected TransportCompatibilityTestSuite(MessageBroker clientBroker, MessageBroker serverBroker) {
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

    private <T> void runWithAck(MessageBroker source, MessageBroker dest, Iterator<Object> destIt, T content) {
        Observable<Void> ack = sniff("ack", source.submitWithAck(content));
        Iterator<Notification<Void>> ackIterator = ack.materialize().toBlocking().getIterator();

        T receivedMsg = (T) destIt.next();
        assertEquals(content, receivedMsg);

        RxBlocking.isCompleted(1000, TimeUnit.SECONDS, dest.acknowledge(receivedMsg));

        assertTrue("Expected successful acknowledgement", ackIterator.next().isOnCompleted());
    }

    public static class RegistrationProtocolTest extends TransportCompatibilityTestSuite {

        public RegistrationProtocolTest(MessageBroker clientBroker, MessageBroker serverBroker) {
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

    public static class DiscoveryProtocolTest extends TransportCompatibilityTestSuite {
        public DiscoveryProtocolTest(MessageBroker clientBroker, MessageBroker serverBroker) {
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
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.HOSTNAME, "newHostname").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.PORTS, Sets.asSet(1111, 2222)).build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.SECURE_PORTS, Sets.asSet(1112, 2223)).build()));
            runServerToClientWithAck(new UpdateInstanceInfo(SampleDelta.StatusDown.build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.HOMEPAGE_URL, "newHomePageURL").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.STATUS_PAGE_URL, "newStatusPageURL").build()));
            runServerToClientWithAck(new UpdateInstanceInfo(builder.withDelta(InstanceInfoField.HEALTHCHECK_URLS, Sets.asSet("http://newHealthCheck1", "http://newHealthCheck2")).build()));
        }
    }
}
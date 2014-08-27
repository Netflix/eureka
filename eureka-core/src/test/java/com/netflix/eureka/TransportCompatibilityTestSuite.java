package com.netflix.eureka;

import java.util.Iterator;

import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.SampleDelta;
import com.netflix.eureka.registry.SampleInterest;
import com.netflix.eureka.transport.MessageBroker;
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

        dest.acknowledge(receivedMsg);

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
            runClientToServerWithAck(new RegisterInterestSet(SampleInterest.MultipleApps.build()));
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
            runServerToClientWithAck(new UpdateInstanceInfo(SampleDelta.StatusUp.build()));
        }
    }
}
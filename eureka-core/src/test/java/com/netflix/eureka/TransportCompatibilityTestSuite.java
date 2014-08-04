package com.netflix.eureka;

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import rx.Observable;

import java.util.Arrays;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;

/**
 * @author Tomasz Bak
 */
public abstract class TransportCompatibilityTestSuite {

    protected final MessageBroker clientBroker;
    protected final MessageBroker serverBroker;
    protected final Iterator<Message> serverIterator;
    protected final Iterator<Message> clientIterator;

    protected TransportCompatibilityTestSuite(MessageBroker clientBroker, MessageBroker serverBroker) {
        this.clientBroker = clientBroker;
        this.serverBroker = serverBroker;
        serverIterator = serverBroker.incoming().toBlocking().getIterator();
        clientIterator = clientBroker.incoming().toBlocking().getIterator();
    }

    public <T> void runClientToServer(T content) {
        clientBroker.submit(new UserContent(content));
        UserContent receivedMsg = (UserContent) serverIterator.next();
        assertEquals(content, receivedMsg.getContent());
    }

    public <T> void runClientToServerWithAck(T content) {
        runWithAck(clientBroker, serverBroker, serverIterator, content);
    }

    public <T> void runServerToClientWithAck(T content) {
        runWithAck(serverBroker, clientBroker, clientIterator, content);
    }

    private <T> void runWithAck(MessageBroker source, MessageBroker dest, Iterator<Message> destIt, T content) {
        Observable<Acknowledgement> ack = source.submitWithAck(new UserContent(content));
        Iterator<Acknowledgement> ackIterator = ack.toBlocking().getIterator();

        UserContentWithAck receivedMsg = (UserContentWithAck) destIt.next();
        assertEquals(content, receivedMsg.getContent());

        dest.acknowledge(receivedMsg);

        assertEquals(receivedMsg.getCorrelationId(), ackIterator.next().getCorrelationId());
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
            runClientToServerWithAck(new Register(SampleInstanceInfo.DiscoveryServer.build()));
        }

        private void unregisterTest() {
            runClientToServerWithAck(new Unregister());
        }

        private void updateTest() {
            runClientToServerWithAck(new Update("key", "value123"));
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
            Interest[] interests = {Interests.forApplication("app1"), Interests.forVip("vip1")};
            runClientToServerWithAck(new RegisterInterestSet(Arrays.asList(interests)));
        }

        private void unregisterInterestSetTest() {
            runClientToServerWithAck(UnregisterInterestSet.INSTANCE);
        }

        private void hearbeatTest() {
            runClientToServer(Heartbeat.INSTANCE);
        }

        private void addInstanceTest() {
            runServerToClientWithAck(new AddInstance(SampleInstanceInfo.DiscoveryServer.build()));
        }

        private void deleteInstanceTest() {
            runServerToClientWithAck(new DeleteInstance("id1"));
        }

        private void updateInstanceInfoTest() {
            runServerToClientWithAck(new UpdateInstanceInfo("someKey", "someValue"));
        }
    }
}
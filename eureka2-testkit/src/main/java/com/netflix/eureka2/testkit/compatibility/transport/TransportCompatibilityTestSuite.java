package com.netflix.eureka2.testkit.compatibility.transport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.model.instance.DeltaBuilder;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoField;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleDelta;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import com.netflix.eureka2.testkit.internal.rx.RxBlocking;
import com.netflix.eureka2.utils.ExtCollections;
import rx.Notification;
import rx.Observable;

import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.DiscoveryServer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public abstract class TransportCompatibilityTestSuite {

    protected final EurekaConnection clientBroker;
    protected final EurekaConnection serverBroker;
    protected final Iterator<Object> serverIterator;
    protected final Iterator<Object> clientIterator;

    protected TransportCompatibilityTestSuite(EurekaConnection clientBroker, EurekaConnection serverBroker) {
        this.clientBroker = clientBroker;
        this.serverBroker = serverBroker;
        serverIterator = serverBroker.incoming().toBlocking().getIterator();
        clientIterator = clientBroker.incoming().toBlocking().getIterator();
    }

    public <T> void runClientToServer(T content) {
        clientBroker.submit(content);
        T receivedMsg = (T) serverIterator.next();
        assertEquals(content, receivedMsg);
    }

    public <T> void runClientToServerWithAck(T content) {
        runWithAck(clientBroker, serverBroker, serverIterator, content);
    }

    public <T> void runServerToClient(T content) {
        serverBroker.submit(content);
        T receivedMsg = (T) clientIterator.next();
        assertEquals(content, receivedMsg);
    }

    public <T> void runServerToClientWithAck(T content) {
        runWithAck(serverBroker, clientBroker, clientIterator, content);
    }

    private <T> void runWithAck(EurekaConnection source, EurekaConnection dest, Iterator<Object> destIt, T content) {
        Observable<Void> ack = source.submitWithAck(content);
        Iterator<Notification<Void>> ackIterator = ack.materialize().toBlocking().getIterator();

        T receivedMsg = (T) destIt.next();
        assertEquals(content, receivedMsg);

        RxBlocking.isCompleted(1000, TimeUnit.SECONDS, dest.acknowledge());

        assertTrue("Expected successful acknowledgement", ackIterator.next().isOnCompleted());
    }

    public static class RegistrationProtocolTest extends TransportCompatibilityTestSuite {

        public RegistrationProtocolTest(EurekaConnection clientBroker, EurekaConnection serverBroker) {
            super(clientBroker, serverBroker);
        }

        public void runTestSuite() {
            registrationTest();
            unregisterTest();
            hearbeatTest();
        }

        private void registrationTest() {
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newRegister(DiscoveryServer.build()));
        }

        private void unregisterTest() {
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newUnregister());
        }

        private void hearbeatTest() {
            runClientToServer(ProtocolModel.getDefaultModel().newHeartbeat());
        }
    }

    public static class ReplicationProtocolTest extends TransportCompatibilityTestSuite {

        private final InstanceInfo instanceInfo = DiscoveryServer.build();

        public ReplicationProtocolTest(EurekaConnection clientBroker, EurekaConnection serverBroker) {
            super(clientBroker, serverBroker);
        }

        public void runTestSuite() {
            handshakeTest();
            registrationTest();
            registrationWithNullsTest();
            unregisterTest();
            hearbeatTest();
        }

        private void handshakeTest() {
            StdSource source = new StdSource(StdSource.Origin.REPLICATED, "testId", 0);
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newReplicationHello(source, 1));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newReplicationHelloReply(source, true));
        }

        private void registrationTest() {
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newAddInstance(instanceInfo));
        }

        private void registrationWithNullsTest() {
            // Verify data cleanup
            HashSet<String> healthCheckUrls = new HashSet<>();
            healthCheckUrls.add(null);
            HashSet<ServicePort> ports = new HashSet<>();
            ports.add(null);

            InstanceInfo emptyInstanceInfo = new StdInstanceInfo.Builder()
                    .withId("id#empty")
                    .withPorts(ports)
                    .withHealthCheckUrls(healthCheckUrls)
                    .build();
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newAddInstance(emptyInstanceInfo));
        }

        private void unregisterTest() {
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newDeleteInstance(instanceInfo.getId()));
        }

        private void hearbeatTest() {
            runClientToServer(ProtocolModel.getDefaultModel().newHeartbeat());
        }
    }

    public static class DiscoveryProtocolTest extends TransportCompatibilityTestSuite {
        public DiscoveryProtocolTest(EurekaConnection clientBroker, EurekaConnection serverBroker) {
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
            streamStateUpdateTest();
        }

        private void registerInterestSetTest() {
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(SampleInterest.DiscoveryInstance.build()));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(SampleInterest.DiscoveryApp.build()));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(SampleInterest.DiscoveryVip.build()));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(SampleInterest.DiscoveryVipSecure.build()));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(SampleInterest.MultipleApps.build()));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(Interests.forFullRegistry()));
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newInterestRegistration(Interests.forNone()));
        }

        private void unregisterInterestSetTest() {
            runClientToServerWithAck(ProtocolModel.getDefaultModel().newUnregisterInterestSet());
        }

        private void hearbeatTest() {
            runClientToServer(ProtocolModel.getDefaultModel().newHeartbeat());
        }

        private void addInstanceTest() {
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newAddInstance(DiscoveryServer.build()));
        }

        private void deleteInstanceTest() {
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newDeleteInstance("id1"));
        }

        private void updateInstanceInfoTest() {
            DeltaBuilder builder = InstanceModel.getDefaultModel().newDelta().withId("id1");
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.APPLICATION_GROUP, "newAppGroup").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.APPLICATION, "newApplication").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.ASG, "newASG").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.VIP_ADDRESS, "newVipAddress").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.SECURE_VIP_ADDRESS, "newSecureVipAddress").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.PORTS, SampleServicePort.httpPorts()).build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(SampleDelta.StatusDown.build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.HOMEPAGE_URL, "newHomePageURL").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.STATUS_PAGE_URL, "newStatusPageURL").build()));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.HEALTHCHECK_URLS, ExtCollections.asSet("http://newHealthCheck1", "http://newHealthCheck2")).build()));

            Map<String, String> metaData = new HashMap<>();
            metaData.put("key1", "value1");
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.META_DATA, metaData).build()));

            DataCenterInfo awsDataCenterInfo = SampleAwsDataCenterInfo.UsEast1a.builder().withInstanceId("newInstanceId").build();
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.DATA_CENTER_INFO, awsDataCenterInfo).build()));
            DataCenterInfo basicDataCenterInfo = LocalDataCenterInfo.fromSystemData();
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.DATA_CENTER_INFO, basicDataCenterInfo).build()));

            // Update with null values (delete semantic)
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newUpdateInstanceInfo(builder.withDelta(InstanceInfoField.APPLICATION, null).build()));
        }

        private void streamStateUpdateTest() {
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newStreamStateUpdate(StreamStateNotification.bufferStartNotification(Interests.forFullRegistry())));
            runServerToClientWithAck(ProtocolModel.getDefaultModel().newStreamStateUpdate(StreamStateNotification.bufferEndNotification(Interests.forFullRegistry())));
        }
    }
}
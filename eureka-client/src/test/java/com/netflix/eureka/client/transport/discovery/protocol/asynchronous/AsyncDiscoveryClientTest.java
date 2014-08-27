package com.netflix.eureka.client.transport.discovery.protocol.asynchronous;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.client.transport.discovery.DiscoveryClient;
import com.netflix.eureka.client.transport.discovery.DiscoveryClientProvider;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.ChangeNotification.Kind;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.SampleDelta;
import com.netflix.eureka.registry.SampleInterest;
import com.netflix.eureka.rx.RxBlocking;
import com.netflix.eureka.rx.RxBlocking.RxItem;
import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka.client.bootstrap.StaticBootstrapResolver.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class AsyncDiscoveryClientTest {

    private MessageBrokerServer server;
    private MessageBroker serverBroker;
    private DiscoveryClient discoveryClient;
    private Iterator<Object> serverMessageIt;

    @Before
    public void setUp() throws Exception {
        server = EurekaTransports.tcpDiscoveryServer(0, Codec.Json).start();

        RxItem<MessageBroker> messageBrokerCollector = RxBlocking.firstFrom(1, TimeUnit.SECONDS, server.clientConnections());
        RxItem<DiscoveryClient> discoveryClientCollector = RxBlocking.firstFrom(1, TimeUnit.SECONDS,
                DiscoveryClientProvider.tcpClientProvider(singleHostResolver("localhost", server.getServerPort()), Codec.Json)
                        .connect());

        serverBroker = messageBrokerCollector.item();
        discoveryClient = discoveryClientCollector.item();

        serverMessageIt = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, serverBroker.incoming());
    }

    @After
    public void tearDown() throws Exception {
        if (discoveryClient != null) {
            discoveryClient.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testRegisterInterests() throws Exception {
        Interest<InstanceInfo> intrest = SampleInterest.DiscoveryApp.build();
        Observable<Void> reply = discoveryClient.registerInterestSet(intrest);

        RegisterInterestSet message = (RegisterInterestSet) serverMessageIt.next();
        assertEquals("Expected registration message", intrest, message.toComposite());

        serverBroker.acknowledge(message);

        assertTrue("Acknowledgement not received", RxBlocking.isCompleted(1, TimeUnit.SECONDS, reply));
    }

    @Test
    public void testUnregisterInterests() throws Exception {
        Observable<Void> reply = discoveryClient.unregisterInterestSet();

        Object message = serverMessageIt.next();
        assertTrue("Expected unregistration message", message instanceof UnregisterInterestSet);

        serverBroker.acknowledge(message);

        assertTrue("Acknowledgement not received", RxBlocking.isCompleted(1, TimeUnit.SECONDS, reply));
    }

    @Test
    public void testHearbeat() throws Exception {
        Observable<Void> reply = discoveryClient.heartbeat();
        assertTrue("Heartbeat not sent", RxBlocking.isCompleted(1, TimeUnit.SECONDS, reply));

        Object message = serverMessageIt.next();
        assertTrue("Expected heartbeat message", message instanceof Heartbeat);
    }

    @Test
    public void testUpdateForAdd() throws Exception {
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, discoveryClient.updates());

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        serverBroker.submit(new AddInstance(instanceInfo));

        ChangeNotification<InstanceInfo> notification = notificationIterator.next();
        assertEquals("Identical instanceinfo expected", instanceInfo, notification.getData());
    }

    @Test
    public void testUpdateForModify() throws Exception {
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, discoveryClient.updates());

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        Delta<?> delta = SampleDelta.StatusUp.builder().withId(instanceInfo.getId()).build();

        serverBroker.submit(new AddInstance(instanceInfo));
        serverBroker.submit(new UpdateInstanceInfo(delta));

        notificationIterator.next();
        ChangeNotification<InstanceInfo> notification = notificationIterator.next();
        assertEquals("Expected modify notification", Kind.Modify, notification.getKind());
    }

    @Test
    public void testUpdateForDelete() throws Exception {
        Iterator<ChangeNotification<InstanceInfo>> notificationIterator = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, discoveryClient.updates());

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        serverBroker.submit(new AddInstance(instanceInfo));
        serverBroker.submit(new DeleteInstance(instanceInfo.getId()));

        notificationIterator.next();
        ChangeNotification<InstanceInfo> notification = notificationIterator.next();
        assertEquals("Expected delete notification", Kind.Delete, notification.getKind());
    }
}
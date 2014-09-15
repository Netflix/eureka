package com.netflix.eureka.client.transport.discovery.protocol.asynchronous;

/**
 * @author Tomasz Bak
 */
public class TcpTransportClientTest {
/*

    private MessageBrokerServer server;
    private MessageBroker serverBroker;
    private ServerConnection discoveryServerConnection;
    private Iterator<Object> serverMessageIt;

    @Before
    public void setUp() throws Exception {
        server = EurekaTransports.tcpDiscoveryServer(0, Codec.Json).start();

        RxItem<MessageBroker> messageBrokerCollector = RxBlocking.firstFrom(1, TimeUnit.SECONDS, server.clientConnections());
        TransportClient discoveryClient = TransportClients.newTcpDiscoveryClient(hostResolver("localhost", server.getServerPort()),
                                                                        Codec.Json);
        RxItem<ServerConnection> discoveryClientCollector = RxBlocking.firstFrom(1, TimeUnit.SECONDS,
                                                                                 discoveryClient
                                                                                         .connect());

        serverBroker = messageBrokerCollector.item();
        discoveryServerConnection = discoveryClientCollector.item();

        serverMessageIt = RxBlocking.iteratorFrom(1, TimeUnit.SECONDS, serverBroker.incoming());
    }

    @After
    public void tearDown() throws Exception {
        if (discoveryServerConnection != null) {
            discoveryServerConnection.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testRegisterInterests() throws Exception {
        Interest<InstanceInfo> intrest = SampleInterest.DiscoveryApp.build();
        Observable<Void> reply = discoveryServerConnection.registerInterestSet(intrest);

        InterestRegistration message = (InterestRegistration) serverMessageIt.next();
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
*/
}
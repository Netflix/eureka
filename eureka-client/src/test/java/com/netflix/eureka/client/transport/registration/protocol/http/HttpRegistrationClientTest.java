package com.netflix.eureka.client.transport.registration.protocol.http;

/**
 * @author Tomasz Bak
 */
public class HttpRegistrationClientTest {
/*

    private static final InstanceInfo INSTANCE_INFO = DiscoveryServer.build();

    private MockHttpRxServer<String, String> server;
    private Iterator<RequestContext<String, String>> requestContextIterator;

    private RegistrationClient client;

    @Before
    public void setUpAuditService() throws Exception {
        server = new MockHttpRxServer<String, String>()
                .withSourceTransformer(new ToStringTransformer())
                .withResultTransformer(new FromStringTransformer())
                .start();

        RegistrationClientProvider<InetSocketAddress> clientProvider = httpClientProvider(hostResolver("localhost", server.getServerPort()));
        client = clientProvider.connect().toBlocking().first();
        requestContextIterator = server.contextIterator();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
        client.shutdown();
    }

    @Test
    public void testRegister() throws Exception {
        Observable<Void> reply = client.register(INSTANCE_INFO);
        Iterator<Void> responseIterator = reply.toBlocking().getIterator();

        RequestContext<String, String> pendingRequest = requestContextIterator.next();
        assertTrue("Expected instanceinfo JSON", pendingRequest.getRequestContent().contains(INSTANCE_INFO.getId()));
        pendingRequest.submitResponse();

        assertTrue("No response body expected", !responseIterator.hasNext());
    }

    @Test
    public void testUpdate() throws Exception {
        Builder instanceInfoBuilder = DiscoveryServer.builder();
        InstanceInfo beforeUpdate = instanceInfoBuilder.build();
        Observable<Void> reply = client.update(instanceInfoBuilder.withHostname("myNewHostName").build());
        Iterator<Void> responseIterator = reply.toBlocking().getIterator();

        RequestContext<String, String> pendingRequest = requestContextIterator.next();
        assertTrue("Invalid request path", pendingRequest.getHttpServerRequest().getPath().endsWith("apps/" + beforeUpdate.getId()));
        assertTrue("Expected instanceinfo JSON", pendingRequest.getRequestContent().contains("myNewHostName"));
        pendingRequest.submitResponse();

        assertTrue("No response body expected", !responseIterator.hasNext());
    }

    @Test
    public void testUnregister() throws Exception {
        Observable<Void> reply = client.unregister(INSTANCE_INFO);
        Iterator<Void> responseIterator = reply.toBlocking().getIterator();

        RequestContext<String, String> pendingRequest = requestContextIterator.next();
        assertEquals("Expected DELETE operation", HttpMethod.DELETE, pendingRequest.getHttpServerRequest().getHttpMethod());
        pendingRequest.submitResponse();

        assertTrue("No response body expected", !responseIterator.hasNext());
    }
*/
}
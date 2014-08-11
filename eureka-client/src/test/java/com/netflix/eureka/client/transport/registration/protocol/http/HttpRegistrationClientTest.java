package com.netflix.eureka.client.transport.registration.protocol.http;

import com.netflix.eureka.SampleInstanceInfo;
import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.client.transport.registration.RegistrationClients;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.rx.MockHttpRxServer;
import com.netflix.eureka.rx.MockHttpRxServer.FromStringTransformer;
import com.netflix.eureka.rx.MockHttpRxServer.RequestContext;
import com.netflix.eureka.rx.MockHttpRxServer.ToStringTransformer;
import io.netty.handler.codec.http.HttpMethod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class HttpRegistrationClientTest {

    private static final InstanceInfo INSTANCE_INFO = SampleInstanceInfo.DiscoveryServer.build();

    private MockHttpRxServer<String, String> server;
    private Iterator<RequestContext<String, String>> requestContextIterator;

    private RegistrationClient client;

    @Before
    public void setUp() throws Exception {
        server = new MockHttpRxServer<String, String>()
                .withSourceTransformer(new ToStringTransformer())
                .withResultTransformer(new FromStringTransformer())
                .start();

        client = RegistrationClients.httpRegistrationClient("localhost", server.getServerPort());
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
        Observable<Void> reply = client.update(INSTANCE_INFO, new Update("someKey", "someValue"));
        Iterator<Void> responseIterator = reply.toBlocking().getIterator();

        RequestContext<String, String> pendingRequest = requestContextIterator.next();
        assertTrue("Invalid request path", pendingRequest.getHttpServerRequest().getPath().endsWith("apps/" + INSTANCE_INFO.getId()));
        assertTrue("Expected instanceinfo JSON", pendingRequest.getRequestContent().contains("someKey"));
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
}
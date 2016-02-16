package com.netflix.discovery;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static com.netflix.discovery.util.EurekaEntityFunctions.toApplications;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.io.IOException;

import javax.ws.rs.core.MediaType;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;

public class EurekaEventListenerTest {
    private static final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);
    private static SimpleEurekaHttpServer eurekaHttpServer;

    @Rule
    public DiscoveryClientResource discoveryClientResource = DiscoveryClientResource.newBuilder()
            .withRegistration(true)
            .withRegistryFetch(true)
            .connectWith(eurekaHttpServer)
            .build();

    /**
     * Share server stub by all tests.
     */
    @BeforeClass
    public static void setUpClass() throws IOException {
        eurekaHttpServer = new SimpleEurekaHttpServer(requestHandler);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (eurekaHttpServer != null) {
            eurekaHttpServer.shutdown();
        }
    }

    @Before
    public void setUp() throws Exception {
        reset(requestHandler);
        when(requestHandler.register(any(InstanceInfo.class))).thenReturn(EurekaHttpResponse.status(204));
        when(requestHandler.cancel(anyString(), anyString())).thenReturn(EurekaHttpResponse.status(200));
        when(requestHandler.getDelta()).thenReturn(
                anEurekaHttpResponse(200, new Applications()).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
    }

    static class CapturingEurekaEventListener implements EurekaEventListener {
        private volatile EurekaEvent event;
        
        @Override
        public void onEvent(EurekaEvent event) {
            this.event = event;
        }
    }
    
    @Test
    public void testCacheRefreshEvent() throws Exception {
        CapturingEurekaEventListener listener = new CapturingEurekaEventListener();

        Applications initialApps = toApplications(discoveryClientResource.getMyInstanceInfo());
        when(requestHandler.getApplications()).thenReturn(
                anEurekaHttpResponse(200, initialApps).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        DiscoveryClient client = (DiscoveryClient) discoveryClientResource.getClient();
        client.registerEventListener(listener);
        client.refreshRegistry();

        assertNotNull(listener.event);
        assertThat(listener.event, is(instanceOf(CacheRefreshedEvent.class)));
    }
}

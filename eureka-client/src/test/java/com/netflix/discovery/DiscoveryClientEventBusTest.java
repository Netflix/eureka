package com.netflix.discovery;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.Subscribe;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static com.netflix.discovery.util.EurekaEntityFunctions.toApplications;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class DiscoveryClientEventBusTest {

    private static final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);
    private static SimpleEurekaHttpServer eurekaHttpServer;

    @Rule
    public DiscoveryClientResource discoveryClientResource = DiscoveryClientResource.newBuilder()
            .withRegistration(false)  // we don't need the registration thread for status change
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

    @Test
    public void testStatusChangeEvent() throws Exception {
        final CountDownLatch eventLatch = new CountDownLatch(1);
        final List<StatusChangeEvent> receivedEvents = new ArrayList<StatusChangeEvent>();
        EventBus eventBus = discoveryClientResource.getEventBus();
        eventBus.registerSubscriber(new Object() {
            @Subscribe
            public void consume(StatusChangeEvent event) {
                receivedEvents.add(event);
                eventLatch.countDown();
            }
        });

        Applications initialApps = toApplications(discoveryClientResource.getMyInstanceInfo());
        when(requestHandler.getApplications()).thenReturn(
                anEurekaHttpResponse(200, initialApps).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        discoveryClientResource.getClient(); // Activates the client

        assertThat(eventLatch.await(10, TimeUnit.SECONDS), is(true));
        assertThat(receivedEvents.size(), is(equalTo(1)));
        assertThat(receivedEvents.get(0), is(notNullValue()));
    }

    @Test
    public void testCacheRefreshEvent() throws Exception {
        InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, "testApp").build();

        // Initial full fetch
        Applications initialApps = instanceGen.takeDelta(1);
        when(requestHandler.getApplications()).thenReturn(
                anEurekaHttpResponse(200, initialApps).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        discoveryClientResource.getClient(); // Activates the client

        // Delta update
        Applications delta = instanceGen.takeDelta(1);
        when(requestHandler.getDelta()).thenReturn(
                anEurekaHttpResponse(200, delta).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        assertThat(discoveryClientResource.awaitCacheUpdate(10, TimeUnit.SECONDS), is(true));
    }
}

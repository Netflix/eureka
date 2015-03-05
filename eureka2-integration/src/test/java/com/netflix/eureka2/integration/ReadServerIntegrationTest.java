package com.netflix.eureka2.integration;

import java.util.Iterator;
import java.util.Set;

import com.netflix.eureka2.client.functions.ChangeNotificationFunctions;
import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources;
import com.netflix.eureka2.testkit.junit.resources.EurekaInterestClientResource;
import com.netflix.eureka2.testkit.junit.resources.ReadServerResource;
import com.netflix.eureka2.testkit.junit.resources.WriteServerResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.netflix.eureka2.interests.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author David Liu
 */
public class ReadServerIntegrationTest {

    private static final int REGISTRY_INITIAL_SIZE = 100;

    @Rule
    public final WriteServerResource writeServerResource = new WriteServerResource();

    @Rule
    public final EurekaExternalResources closeableResources = new EurekaExternalResources();

    private EmbeddedWriteServer writeServer;

    @Before
    public void setUp() throws Exception {
        writeServer = writeServerResource.getServer();
    }

    /**
     * Subscribe to Eureka read server, that has not uploaded yet initial content
     * from write server. Write server batching markers shell be propagated to the client
     * and a client should get all data followed by single buffer sentinel.
     */
    @Test(timeout = 60000)
    public void testColdReadCacheDataBatching() throws Exception {
        fillUpRegistry(writeServer.getEurekaServerRegistry(), REGISTRY_INITIAL_SIZE);
        while (writeServer.getEurekaServerRegistry().size() <= REGISTRY_INITIAL_SIZE) {
            Thread.sleep(1);
        }

        // Bootstrap read server and connect Eureka client immediately
        ReadServerResource readServerResource = bootstrapReadServer();
        EurekaInterestClient eurekaClient = connectEurekaClient(readServerResource.getDiscoveryResolver()).getEurekaClient();

        ExtTestSubscriber<Set<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry())
                .compose(ChangeNotificationFunctions.<InstanceInfo>buffers())
                .compose(ChangeNotificationFunctions.<InstanceInfo>snapshots())
                .subscribe(testSubscriber);

        // We should always get in the first batch all entries
        Set<InstanceInfo> initialSet = testSubscriber.takeNextOrWait();
        assertThat(initialSet.size(), is(greaterThan(REGISTRY_INITIAL_SIZE)));
    }

    /**
     * Subscribe to Eureka read server, that has all data in its own registry.
     * Read server registry batching markers shell be propagated to the client
     * and a client should get all data followed by single buffer sentinel.
     */
    @Test(timeout = 60000)
    public void testHotCacheDataBatching() throws Exception {
        // Bootstrap read server and connect Eureka client immediately
        ReadServerResource readServerResource = bootstrapReadServer();
        EurekaInterestClient eurekaClient = connectEurekaClient(readServerResource.getDiscoveryResolver()).getEurekaClient();

        // Fill in the registry
        fillUpRegistry(writeServer.getEurekaServerRegistry(), REGISTRY_INITIAL_SIZE);

        // Connect with a client and take all entries, to be sure that read server registry is hot
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry()).filter(dataOnlyFilter()).subscribe(testSubscriber);
        testSubscriber.takeNextOrWait(REGISTRY_INITIAL_SIZE + 2);
        eurekaClient.shutdown();

        // Now connect again
        eurekaClient = connectEurekaClient(readServerResource.getDiscoveryResolver()).getEurekaClient();
        testSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);
        for (int i = 0; i < REGISTRY_INITIAL_SIZE + 2; i++) {
            testSubscriber.takeNextOrWait();
        }

        ExtTestSubscriber<Set<InstanceInfo>> snapshotSubscriber = new ExtTestSubscriber<>();
        eurekaClient.forInterest(Interests.forFullRegistry())
                .compose(ChangeNotificationFunctions.<InstanceInfo>buffers())
                .compose(ChangeNotificationFunctions.<InstanceInfo>snapshots())
                .subscribe(snapshotSubscriber);

        // We should always get in the first batch all entries
        Set<InstanceInfo> initialSet = snapshotSubscriber.takeNextOrWait();
        assertThat(initialSet.size(), is(equalTo(REGISTRY_INITIAL_SIZE + 2)));
    }

    private EurekaInterestClientResource connectEurekaClient(ServerResolver serverResolver) {
        EurekaInterestClientResource resource = new EurekaInterestClientResource(serverResolver);
        return closeableResources.connect(resource);
    }

    private ReadServerResource bootstrapReadServer() {
        return closeableResources.connect(new ReadServerResource(writeServerResource));
    }

    private static void fillUpRegistry(SourcedEurekaRegistry<InstanceInfo> eurekaServerRegistry, int registryInitialSize) {
        Iterator<InstanceInfo> instanceIt = SampleInstanceInfo.collectionOf("testInstance#", SampleInstanceInfo.CliServer.build());
        Source source = new Source(Origin.LOCAL, "write1");
        for (int i = 0; i < registryInitialSize; i++) {
            eurekaServerRegistry.register(instanceIt.next(), source).subscribe();
        }
    }
}

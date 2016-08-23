package com.netflix.eureka.cluster;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.shared.transport.ClusterSampleData;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.DefaultServerCodecs;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class PeerEurekaNodesTest {

    private static final String PEER_EUREKA_URL_A = "http://a.eureka.test";
    private static final String PEER_EUREKA_URL_B = "http://b.eureka.test";
    private static final String PEER_EUREKA_URL_C = "http://c.eureka.test";

    private final PeerAwareInstanceRegistry registry = mock(PeerAwareInstanceRegistry.class);

    private final TestablePeerEurekaNodes peerEurekaNodes = new TestablePeerEurekaNodes(registry, ClusterSampleData.newEurekaServerConfig());

    @Test
    public void testInitialStartupShutdown() throws Exception {
        peerEurekaNodes.withPeerUrls(PEER_EUREKA_URL_A);

        // Start
        peerEurekaNodes.start();

        PeerEurekaNode peerNode = getPeerNode(PEER_EUREKA_URL_A);
        assertThat(peerNode, is(notNullValue()));

        // Shutdown
        peerEurekaNodes.shutdown();
        verify(peerNode, times(1)).shutDown();
    }

    @Test
    public void testReloadWithNoPeerChange() throws Exception {
        // Start
        peerEurekaNodes.withPeerUrls(PEER_EUREKA_URL_A);
        peerEurekaNodes.start();

        PeerEurekaNode peerNode = getPeerNode(PEER_EUREKA_URL_A);
        assertThat(peerEurekaNodes.awaitNextReload(60, TimeUnit.SECONDS), is(true));
        assertThat(getPeerNode(PEER_EUREKA_URL_A), is(equalTo(peerNode)));
    }

    @Test
    public void testReloadWithPeerUpdates() throws Exception {
        // Start
        peerEurekaNodes.withPeerUrls(PEER_EUREKA_URL_A);
        peerEurekaNodes.start();
        PeerEurekaNode peerNodeA = getPeerNode(PEER_EUREKA_URL_A);

        // Add one more peer
        peerEurekaNodes.withPeerUrls(PEER_EUREKA_URL_A, PEER_EUREKA_URL_B);

        assertThat(peerEurekaNodes.awaitNextReload(60, TimeUnit.SECONDS), is(true));
        assertThat(getPeerNode(PEER_EUREKA_URL_A), is(notNullValue()));
        assertThat(getPeerNode(PEER_EUREKA_URL_B), is(notNullValue()));

        // Remove first peer, and add yet another one
        peerEurekaNodes.withPeerUrls(PEER_EUREKA_URL_B, PEER_EUREKA_URL_C);
        assertThat(peerEurekaNodes.awaitNextReload(60, TimeUnit.SECONDS), is(true));
        assertThat(getPeerNode(PEER_EUREKA_URL_A), is(nullValue()));
        assertThat(getPeerNode(PEER_EUREKA_URL_B), is(notNullValue()));
        assertThat(getPeerNode(PEER_EUREKA_URL_C), is(notNullValue()));

        verify(peerNodeA, times(1)).shutDown();
    }

    private PeerEurekaNode getPeerNode(String peerEurekaUrl) {
        for (PeerEurekaNode node : peerEurekaNodes.getPeerEurekaNodes()) {
            if (node.getServiceUrl().equals(peerEurekaUrl)) {
                return node;
            }
        }
        return null;
    }

    static class TestablePeerEurekaNodes extends PeerEurekaNodes {

        private AtomicReference<List<String>> peerUrlsRef = new AtomicReference<>(Collections.<String>emptyList());
        private final ConcurrentHashMap<String, PeerEurekaNode> peerEurekaNodeByUrl = new ConcurrentHashMap<>();
        private final AtomicInteger reloadCounter = new AtomicInteger();

        TestablePeerEurekaNodes(PeerAwareInstanceRegistry registry, EurekaServerConfig serverConfig) {
            super(registry,
                    serverConfig,
                    new DefaultEurekaClientConfig(),
                    new DefaultServerCodecs(serverConfig),
                    mock(ApplicationInfoManager.class)
            );
        }

        void withPeerUrls(String... peerUrls) {
            this.peerUrlsRef.set(Arrays.asList(peerUrls));
        }

        boolean awaitNextReload(long timeout, TimeUnit timeUnit) throws InterruptedException {
            int lastReloadCounter = reloadCounter.get();
            long endTime = System.currentTimeMillis() + timeUnit.toMillis(timeout);
            while (endTime > System.currentTimeMillis() && lastReloadCounter == reloadCounter.get()) {
                Thread.sleep(10);
            }
            return lastReloadCounter != reloadCounter.get();
        }

        @Override
        protected void updatePeerEurekaNodes(List<String> newPeerUrls) {
            super.updatePeerEurekaNodes(newPeerUrls);
            reloadCounter.incrementAndGet();
        }

        @Override
        protected List<String> resolvePeerUrls() {
            return peerUrlsRef.get();
        }

        @Override
        protected PeerEurekaNode createPeerEurekaNode(String peerEurekaNodeUrl) {
            if (peerEurekaNodeByUrl.containsKey(peerEurekaNodeUrl)) {
                throw new IllegalStateException("PeerEurekaNode for URL " + peerEurekaNodeUrl + " is already created");
            }
            PeerEurekaNode peerEurekaNode = mock(PeerEurekaNode.class);
            when(peerEurekaNode.getServiceUrl()).thenReturn(peerEurekaNodeUrl);
            return peerEurekaNode;
        }
    }
}
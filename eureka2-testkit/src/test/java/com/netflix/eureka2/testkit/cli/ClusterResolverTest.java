package com.netflix.eureka2.testkit.cli;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.testkit.cli.bootstrap.ClusterResolver;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ClusterResolverTest {

    @Rule
    public final EurekaDeploymentResource deploymentResource = anEurekaDeploymentResource(1, 1).build();

    private final TestScheduler testScheduler = Schedulers.test();

    @Test
    public void testFullClusterResolve() throws Exception {
        EmbeddedWriteServer writeServer = deploymentResource.getEurekaDeployment().getWriteCluster().getServer(0);
        ClusterResolver resolver = new ClusterResolver("localhost", writeServer.getInterestPort(), null, null, null, Schedulers.io());
        ExtTestSubscriber<ClusterTopology> clusterTopologySubscriber = new ExtTestSubscriber<>();
        resolver.connect().subscribe(clusterTopologySubscriber);

        ClusterTopology topology = clusterTopologySubscriber.takeNext(30, TimeUnit.SECONDS);
        assertThat(topology, is(notNullValue()));
        assertThat(topology.getWriteServers().size(), is(equalTo(1)));
        assertThat(topology.getReadServers().size(), is(equalTo(1)));
    }

    @Test
    public void testReadClusterResolve() throws Exception {
        EmbeddedReadServer readServer = deploymentResource.getEurekaDeployment().getReadCluster().getServer(0);
        ClusterResolver resolver = new ClusterResolver("localhost", readServer.getInterestPort(), null, null, null, testScheduler);
        ExtTestSubscriber<ClusterTopology> clusterTopologySubscriber = new ExtTestSubscriber<>();
        resolver.connect().subscribe(clusterTopologySubscriber);

        ClusterTopology topology = clusterTopologySubscriber.takeNext(30, TimeUnit.SECONDS);
        assertThat(topology, is(notNullValue()));
        assertThat(topology.getWriteServers().size(), is(equalTo(0)));
        assertThat(topology.getReadServers().size(), is(equalTo(1)));
    }
}
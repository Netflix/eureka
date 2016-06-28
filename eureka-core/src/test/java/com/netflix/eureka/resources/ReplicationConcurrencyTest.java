package com.netflix.eureka.resources;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * A pseudo mock test to test concurrent scenarios to do with registration and replication of cluster setups
 * with 1+ eureka servers
 *
 * @author David Liu
 */
public class ReplicationConcurrencyTest {

    private String id;
    private String appName;

    private InstanceInfo instance1;
    private InstanceInfo instance2;

    private MockServer server1;
    private MockServer server2;

    private InstanceInfo server1Sees;
    private InstanceInfo server2Sees;

    @Before
    public void setUp() throws Exception {
        InstanceInfo seed = InstanceInfoGenerator.takeOne();
        id = seed.getId();
        appName = seed.getAppName();

        // set up test instances
        instance1 = InstanceInfo.Builder.newBuilder()
                .setInstanceId(id)
                .setAppName(appName)
                .setHostName(seed.getHostName())
                .setIPAddr(seed.getIPAddr())
                .setDataCenterInfo(seed.getDataCenterInfo())
                .setStatus(InstanceInfo.InstanceStatus.STARTING)
                .setLastDirtyTimestamp(11111l)
                .build();

        instance2 = new InstanceInfo.Builder(seed)
                .setInstanceId(id)
                .setAppName(appName)
                .setHostName(seed.getHostName())
                .setIPAddr(seed.getIPAddr())
                .setDataCenterInfo(seed.getDataCenterInfo())
                .setStatus(InstanceInfo.InstanceStatus.UP)
                .setLastDirtyTimestamp(22222l)
                .build();

        assertThat(instance1.getStatus(), not(equalTo(instance2.getStatus())));

        // set up server1 with no replication anywhere
        PeerEurekaNodes server1Peers = Mockito.mock(PeerEurekaNodes.class);
        Mockito.when(server1Peers.getPeerEurekaNodes()).thenReturn(Collections.<PeerEurekaNode>emptyList());
        server1 = new MockServer(appName, server1Peers);

        // set up server2
        PeerEurekaNodes server2Peers = Mockito.mock(PeerEurekaNodes.class);
        Mockito.when(server2Peers.getPeerEurekaNodes()).thenReturn(Collections.<PeerEurekaNode>emptyList());
        server2 = new MockServer(appName, server2Peers);

        // register with server1
        server1.applicationResource.addInstance(instance1, "false"/* isReplication */);  // STARTING
        server1Sees = server1.registry.getInstanceByAppAndId(appName, id);
        assertThat(server1Sees, equalTo(instance1));

        // update (via a register) with server2
        server2.applicationResource.addInstance(instance2, "false"/* isReplication */);  // UP
        server2Sees = server2.registry.getInstanceByAppAndId(appName, id);
        assertThat(server2Sees, equalTo(instance2));

        // make sure data in server 1 is "older"
        assertThat(server2Sees.getLastDirtyTimestamp() > server1Sees.getLastDirtyTimestamp(), is(true));
    }

    /**
     * this test tests a scenario where multiple registration and update requests for a single client is sent to
     * different eureka servers before replication can occur between them
     */
    @Test
    public void testReplicationWithRegistrationAndUpdateOnDifferentServers() throws Exception {
        // now simulate server1 (delayed) replication to server2.
        // without batching this is done by server1 making a REST call to the register endpoint of server2 with
        // replication=true
        server2.applicationResource.addInstance(instance1, "true");

        // verify that server2's "newer" info is (or is not) overridden
        // server2 should still see instance2 even though server1 tried to replicate across server1
        InstanceInfo newServer2Sees = server2.registry.getInstanceByAppAndId(appName, id);
        assertThat(newServer2Sees.getStatus(), equalTo(instance2.getStatus()));

        // now let server2 replicate to server1
        server1.applicationResource.addInstance(newServer2Sees, "true");

        // verify that server1 now have the updated info from server2
        InstanceInfo newServer1Sees = server1.registry.getInstanceByAppAndId(appName, id);
        assertThat(newServer1Sees.getStatus(), equalTo(instance2.getStatus()));
    }



    private static class MockServer {
        public final ApplicationResource applicationResource;
        public final PeerReplicationResource replicationResource;

        public final PeerAwareInstanceRegistry registry;

        public MockServer(String appName, PeerEurekaNodes peerEurekaNodes) throws Exception {
            ApplicationInfoManager infoManager = new ApplicationInfoManager(new MyDataCenterInstanceConfig());

            DefaultEurekaServerConfig serverConfig = Mockito.spy(new DefaultEurekaServerConfig());
            DefaultEurekaClientConfig clientConfig = new DefaultEurekaClientConfig();
            ServerCodecs serverCodecs = new DefaultServerCodecs(serverConfig);
            EurekaClient eurekaClient = Mockito.mock(EurekaClient.class);

            Mockito.doReturn("true").when(serverConfig).getExperimental("registry.registration.ignoreIfDirtyTimestampIsOlder");

            this.registry = new PeerAwareInstanceRegistryImpl(serverConfig, clientConfig, serverCodecs, eurekaClient);
            this.registry.init(peerEurekaNodes);

            this.applicationResource = new ApplicationResource(appName, serverConfig, registry);

            EurekaServerContext serverContext = Mockito.mock(EurekaServerContext.class);
            Mockito.when(serverContext.getServerConfig()).thenReturn(serverConfig);
            Mockito.when(serverContext.getRegistry()).thenReturn(registry);
            this.replicationResource = new PeerReplicationResource(serverContext);
        }
    }

}

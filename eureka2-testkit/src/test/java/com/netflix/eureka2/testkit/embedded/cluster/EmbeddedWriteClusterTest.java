package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.server.resolver.ClusterAddress.ServiceType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedWriteCluster.WriteClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.observers.TestSubscriber;

import static java.util.Collections.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteClusterTest {

    private final EmbeddedWriteServer writeServer = mock(EmbeddedWriteServer.class);

    private EmbeddedWriteCluster writeCluster;

    @Before
    public void setUp() throws Exception {
        writeCluster = new EmbeddedWriteCluster(false, false, false) {
            @Override
            protected EmbeddedWriteServer newServer(WriteServerConfig config) {
                return writeServer;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        writeCluster.shutdown();
    }

    @Test(timeout = 60000)
    public void testClusterScaleUp() throws Exception {
        writeCluster.scaleUpBy(1);

        verify(writeServer, times(1)).start();

        // Verify replication peers observable
        TestSubscriber<ChangeNotification<Server>> replicationPeerSubscriber = new TestSubscriber<>();
        writeCluster.resolvePeers(ServiceType.Replication).subscribe(replicationPeerSubscriber);

        replicationPeerSubscriber.assertNoErrors();
        assertThat(replicationPeerSubscriber.getOnNextEvents().size(), is(equalTo(1)));

        // Verify registration resolver returns the new server
        TestSubscriber<Server> registrationServerSubscriber = new TestSubscriber<>();
        writeCluster.registrationResolver().resolve().subscribe(registrationServerSubscriber);

        Server expectedServer = new Server("localhost", EmbeddedWriteCluster.WRITE_SERVER_PORTS_FROM);
        registrationServerSubscriber.assertReceivedOnNext(singletonList(expectedServer));

        // Verify discovery resolver returns the new server
        TestSubscriber<Server> discoveryServerSubscriber = new TestSubscriber<>();
        writeCluster.interestResolver().resolve().subscribe(discoveryServerSubscriber);

        expectedServer = new Server("localhost", EmbeddedWriteCluster.WRITE_SERVER_PORTS_FROM + 1);
        discoveryServerSubscriber.assertReceivedOnNext(singletonList(expectedServer));
    }

    @Test(timeout = 60000)
    public void testClusterScaleDown() throws Exception {
        writeCluster.scaleUpBy(2);

        // Subscribe to replication peer before scale down to catch server remove update
        TestSubscriber<ChangeNotification<Server>> replicationPeerSubscriber = new TestSubscriber<>();
        writeCluster.resolvePeers(ServiceType.Replication).subscribe(replicationPeerSubscriber);

        // Now scale down
        writeCluster.scaleDownBy(1);
        verify(writeServer, times(1)).shutdown();

        // Verify we have server remove
        List<ChangeNotification<Server>> updates = replicationPeerSubscriber.getOnNextEvents();
        assertThat(updates.size(), is(equalTo(3)));
        assertThat(updates.get(2).getKind(), is(equalTo(Kind.Delete)));
    }

    @Test(timeout = 60000)
    public void testReportContent() throws Exception {
        writeCluster.scaleUpByOne();

        WriteClusterReport report = writeCluster.clusterReport();
        assertThat(report.getServerReports().size(), is(equalTo(1)));
    }
}
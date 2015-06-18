package com.netflix.eureka2.testkit.embedded.cluster;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster.ReadClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadClusterTest {

    private final EmbeddedReadServer readServer = mock(EmbeddedReadServer.class);

    private ServerResolver registrationResolver;
    private ServerResolver discoveryResolver;

    private EmbeddedReadCluster readCluster;

    @Before
    public void setUp() throws Exception {
        readCluster = new EmbeddedReadCluster(registrationResolver, discoveryResolver, false, false, true, null) {
            @Override
            protected EmbeddedReadServer newServer(EurekaServerConfig config) {
                return readServer;
            }
        };
    }

    @Test(timeout = 60000)
    public void testClusterScaleUp() throws Exception {
        readCluster.scaleUpBy(1);
        verify(readServer, times(1)).start();
    }

    @Test(timeout = 60000)
    public void testClusterScaleDown() throws Exception {
        readCluster.scaleUpBy(1);
        readCluster.scaleDownBy(1);
        verify(readServer, times(1)).shutdown();
    }

    @Test(timeout = 60000)
    public void testReportContent() throws Exception {
        readCluster.scaleUpByOne();

        ReadClusterReport report = readCluster.clusterReport();
        assertThat(report.getServerReports().size(), is(equalTo(1)));
    }
}
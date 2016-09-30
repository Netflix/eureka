package com.netflix.eureka.cluster;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.EurekaJacksonCodec;
import com.netflix.discovery.shared.transport.ClusterSampleData;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.eureka.resources.DefaultServerCodecs;
import com.netflix.eureka.resources.ServerCodecs;
import com.netflix.eureka.transport.JerseyReplicationClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Ideally we would test client/server REST layer together as an integration test, where server side has mocked
 * service layer. Right now server side REST has to much logic, so this test would be equal to testing everything.
 * Here we test only client side REST communication.
 *
 * @author Tomasz Bak
 */
public class JerseyReplicationClientTest {

    @Rule
    public MockServerRule serverMockRule = new MockServerRule(this);
    private MockServerClient serverMockClient;

    private JerseyReplicationClient replicationClient;

    private final EurekaServerConfig config = new DefaultEurekaServerConfig();
    private final ServerCodecs serverCodecs = new DefaultServerCodecs(config);
    private final InstanceInfo instanceInfo = ClusterSampleData.newInstanceInfo(1);

    @Before
    public void setUp() throws Exception {
        replicationClient = JerseyReplicationClient.createReplicationClient(
                config, serverCodecs, "http://localhost:" + serverMockRule.getHttpPort() + "/eureka/v2"
        );
    }

    @After
    public void tearDown() {
        if (serverMockClient != null) {
            serverMockClient.reset();
        }
    }

    @Test
    public void testRegistrationReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("POST")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName())
        ).respond(
                response().withStatusCode(200)
        );

        EurekaHttpResponse<Void> response = replicationClient.register(instanceInfo);
        assertThat(response.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testCancelReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("DELETE")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId())
        ).respond(
                response().withStatusCode(204)
        );

        EurekaHttpResponse<Void> response = replicationClient.cancel(instanceInfo.getAppName(), instanceInfo.getId());
        assertThat(response.getStatusCode(), is(equalTo(204)));
    }

    @Test
    public void testHeartbeatReplicationWithNoResponseBody() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId())
        ).respond(
                response().withStatusCode(200)
        );

        EurekaHttpResponse<InstanceInfo> response = replicationClient.sendHeartBeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, InstanceStatus.DOWN);
        assertThat(response.getStatusCode(), is(equalTo(200)));
        assertThat(response.getEntity(), is(nullValue()));
    }

    @Test
    public void testHeartbeatReplicationWithResponseBody() throws Exception {
        InstanceInfo remoteInfo = new InstanceInfo(this.instanceInfo);
        remoteInfo.setStatus(InstanceStatus.DOWN);
        byte[] responseBody = toGzippedJson(remoteInfo);

        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/apps/" + this.instanceInfo.getAppName() + '/' + this.instanceInfo.getId())
        ).respond(
                response()
                        .withStatusCode(Status.CONFLICT.getStatusCode())
                        .withHeader(header("Content-Type", MediaType.APPLICATION_JSON))
                        .withHeader(header("Content-Encoding", "gzip"))
                        .withBody(responseBody)
        );

        EurekaHttpResponse<InstanceInfo> response = replicationClient.sendHeartBeat(this.instanceInfo.getAppName(), this.instanceInfo.getId(), this.instanceInfo, null);
        assertThat(response.getStatusCode(), is(equalTo(Status.CONFLICT.getStatusCode())));
        assertThat(response.getEntity(), is(notNullValue()));
    }

    @Test
    public void testAsgStatusUpdateReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/asg/" + instanceInfo.getASGName() + "/status")
        ).respond(
                response().withStatusCode(200)
        );

        EurekaHttpResponse<Void> response = replicationClient.statusUpdate(instanceInfo.getASGName(), ASGStatus.ENABLED);
        assertThat(response.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testStatusUpdateReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId() + "/status")
        ).respond(
                response().withStatusCode(200)
        );

        EurekaHttpResponse<Void> response = replicationClient.statusUpdate(instanceInfo.getAppName(), instanceInfo.getId(), InstanceStatus.DOWN, instanceInfo);
        assertThat(response.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testDeleteStatusOverrideReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("DELETE")
                        .withHeader(header(PeerEurekaNode.HEADER_REPLICATION, "true"))
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId() + "/status")
        ).respond(
                response().withStatusCode(204)
        );

        EurekaHttpResponse<Void> response = replicationClient.deleteStatusOverride(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo);
        assertThat(response.getStatusCode(), is(equalTo(204)));
    }

    private static byte[] toGzippedJson(InstanceInfo remoteInfo) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(bos);
        EurekaJacksonCodec.getInstance().writeTo(remoteInfo, gos);
        gos.flush();
        return bos.toByteArray();
    }
}
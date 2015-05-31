package com.netflix.eureka.cluster;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.EurekaJacksonCodec;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.cluster.HttpReplicationClient.HttpResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.Header;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
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

    private final InstanceInfo instanceInfo = ClusterSampleData.newInstanceInfo(1);

    @Before
    public void setUp() throws Exception {
        replicationClient = new JerseyReplicationClient(config, "http://localhost:" + serverMockRule.getHttpPort() + "/eureka/v2");
    }

    @Test
    public void testRegistrationReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("POST")
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName())
        ).respond(
                response().withStatusCode(200)
        );

        HttpResponse<Void> response = replicationClient.register(instanceInfo);
        assertThat(response.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testCancelReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("DELETE")
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId())
        ).respond(
                response().withStatusCode(204)
        );

        HttpResponse<Void> response = replicationClient.cancel(instanceInfo.getAppName(), instanceInfo.getId());
        assertThat(response.getStatusCode(), is(equalTo(204)));
    }

    @Test
    public void testHeartbeatReplicationWithNoResponseBody() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId())
        ).respond(
                response().withStatusCode(200)
        );

        HttpResponse<InstanceInfo> response = replicationClient.sendHeartBeat(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo, InstanceStatus.DOWN);
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
                        .withPath("/eureka/v2/apps/" + this.instanceInfo.getAppName() + '/' + this.instanceInfo.getId())
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(Header.header("Content-Type", MediaType.APPLICATION_JSON))
                        .withHeader(Header.header("Content-Encoding", "gzip"))
                        .withBody(responseBody)
        );

        HttpResponse<InstanceInfo> response = replicationClient.sendHeartBeat(this.instanceInfo.getAppName(), this.instanceInfo.getId(), this.instanceInfo, null);
        assertThat(response.getStatusCode(), is(equalTo(200)));
        assertThat(response.getEntity(), is(notNullValue()));
    }

    @Test
    public void testAsgStatusUpdateReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withPath("/eureka/v2/asg/" + instanceInfo.getASGName() + "/status")
        ).respond(
                response().withStatusCode(200)
        );

        HttpResponse<Void> response = replicationClient.statusUpdate(instanceInfo.getASGName(), ASGStatus.ENABLED);
        assertThat(response.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testStatusUpdateReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("PUT")
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId() + "/status")
        ).respond(
                response().withStatusCode(200)
        );

        HttpResponse<Void> response = replicationClient.statusUpdate(instanceInfo.getAppName(), instanceInfo.getId(), InstanceStatus.DOWN, instanceInfo);
        assertThat(response.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testDeleteStatusOverrideReplication() throws Exception {
        serverMockClient.when(
                request()
                        .withMethod("DELETE")
                        .withPath("/eureka/v2/apps/" + instanceInfo.getAppName() + '/' + instanceInfo.getId() + "/status")
        ).respond(
                response().withStatusCode(204)
        );

        HttpResponse<Void> response = replicationClient.deleteStatusOverride(instanceInfo.getAppName(), instanceInfo.getId(), instanceInfo);
        assertThat(response.getStatusCode(), is(equalTo(204)));
    }

    private static byte[] toGzippedJson(InstanceInfo remoteInfo) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        GZIPOutputStream gos = new GZIPOutputStream(bos);
        EurekaJacksonCodec.getInstance().writeWithEnvelopeTo(remoteInfo, gos);
        gos.flush();
        return bos.toByteArray();
    }
}
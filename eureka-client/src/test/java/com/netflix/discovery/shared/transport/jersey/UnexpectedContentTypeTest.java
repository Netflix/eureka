package com.netflix.discovery.shared.transport.jersey;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author rbolles on 12/19/19.
 */
public class UnexpectedContentTypeTest {

    protected static final String CLIENT_APP_NAME = "unexpectedContentTypeTest";
    private JerseyApplicationClient jerseyHttpClient;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort().dynamicHttpsPort()); // No-args constructor defaults to port 8080

    @Before
    public void setUp() throws Exception {
        TransportClientFactory clientFactory = JerseyEurekaHttpClientFactory.newBuilder()
                .withClientName(CLIENT_APP_NAME)
                .build();

        String uri = String.format("http://localhost:%s/v2/", wireMockRule.port());

        jerseyHttpClient = (JerseyApplicationClient) clientFactory.newClient(new DefaultEndpoint(uri));
    }

    @Test
    public void testSendHeartBeatReceivesUnexpectedHtmlResponse() {
        long lastDirtyTimestamp = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();

        stubFor(put(urlPathEqualTo("/v2/apps/" + CLIENT_APP_NAME + "/" + uuid))
                .withQueryParam("status", equalTo("UP"))
                .withQueryParam("lastDirtyTimestamp", equalTo(lastDirtyTimestamp + ""))
                .willReturn(aResponse()
                        .withStatus(502)
                        .withHeader("Content-Type", "text/HTML")
                        .withBody("<html><body>Something went wrong in Apacache</body></html>")));

        InstanceInfo instanceInfo = mock(InstanceInfo.class);
        when(instanceInfo.getStatus()).thenReturn(InstanceInfo.InstanceStatus.UP);
        when(instanceInfo.getLastDirtyTimestamp()).thenReturn(lastDirtyTimestamp);

        EurekaHttpResponse<InstanceInfo> response = jerseyHttpClient.sendHeartBeat(CLIENT_APP_NAME, uuid, instanceInfo, null);

        verify(putRequestedFor(urlPathEqualTo("/v2/apps/" + CLIENT_APP_NAME + "/" + uuid))
                .withQueryParam("status", equalTo("UP"))
                .withQueryParam("lastDirtyTimestamp", equalTo(lastDirtyTimestamp + ""))
        );

        assertThat(response.getStatusCode()).as("status code").isEqualTo(502);
        assertThat(response.getEntity()).as("instance info").isNull();
    }

    @After
    public void tearDown() throws Exception {
        if (jerseyHttpClient != null) {
            jerseyHttpClient.shutdown();
        }
    }

}

/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.transport;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.StaticClusterResolver;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaHttpClientsTest {

    private static final InstanceInfo MY_INSTANCE = InstanceInfoGenerator.newBuilder(1, "myApp").build().first();
    private final EurekaClientConfig clientConfig = mock(EurekaClientConfig.class);
    private final EurekaInstanceConfig instanceConfig = mock(EurekaInstanceConfig.class);
    private final ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(instanceConfig, MY_INSTANCE);

    private final EurekaHttpClient writeRequestHandler = mock(EurekaHttpClient.class);
    private final EurekaHttpClient readRequestHandler = mock(EurekaHttpClient.class);

    private SimpleEurekaHttpServer writeServer;
    private SimpleEurekaHttpServer readServer;

    private ClusterResolver clusterResolver;
    private EurekaHttpClientFactory clientFactory;

    private String readServerURI;

    private final InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, 1).build();

    @Before
    public void setUp() throws IOException {
        when(clientConfig.getEurekaServerTotalConnectionsPerHost()).thenReturn(10);
        when(clientConfig.getEurekaServerTotalConnections()).thenReturn(10);

        writeServer = new SimpleEurekaHttpServer(writeRequestHandler);
        clusterResolver = new StaticClusterResolver("regionA", new EurekaEndpoint("localhost", writeServer.getServerPort(), false, "/v2/", null));

        readServer = new SimpleEurekaHttpServer(readRequestHandler);
        readServerURI = "http://localhost:" + readServer.getServerPort();

        clientFactory = EurekaHttpClients.createStandardClientFactory(clientConfig, applicationInfoManager, clusterResolver);
    }

    @After
    public void tearDown() throws Exception {
        if (writeServer != null) {
            writeServer.shutdown();
        }
        if (readServer != null) {
            readServer.shutdown();
        }
        if (clientFactory != null) {
            clientFactory.shutdown();
        }
    }

    @Test
    public void testCanonicalClient() throws Exception {
        Applications apps = instanceGen.toApplications();

        when(writeRequestHandler.getApplications()).thenReturn(
                EurekaHttpResponse.<Applications>anEurekaHttpResponse(302)
                        .withHeader("Location", readServerURI + "/v2/apps")
                        .build()
        );

        when(readRequestHandler.getApplications()).thenReturn(
                EurekaHttpResponse.<Applications>anEurekaHttpResponse(200)
                        .withEntity(apps)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .build()
        );

        EurekaHttpClient eurekaHttpClient = clientFactory.create();

        EurekaHttpResponse<Applications> result = eurekaHttpClient.getApplications();

        assertThat(result.getStatusCode(), is(equalTo(200)));
        assertThat(EurekaEntityComparators.equal(result.getEntity(), apps), is(true));
    }
}
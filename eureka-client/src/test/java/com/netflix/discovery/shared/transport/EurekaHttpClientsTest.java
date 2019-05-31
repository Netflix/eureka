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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.ClosableResolver;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.resolver.EndpointRandomizer;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.ResolverUtils;
import com.netflix.discovery.shared.resolver.StaticClusterResolver;
import com.netflix.discovery.shared.resolver.aws.ApplicationsResolver;
import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.shared.resolver.aws.EurekaHttpResolver;
import com.netflix.discovery.shared.resolver.aws.TestEurekaHttpResolver;
import com.netflix.discovery.shared.transport.jersey.Jersey1TransportClientFactories;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaHttpClientsTest {

    private static final InstanceInfo MY_INSTANCE = InstanceInfoGenerator.newBuilder(1, "myApp").build().first();
    private final EurekaInstanceConfig instanceConfig = mock(EurekaInstanceConfig.class);
    private final ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(instanceConfig, MY_INSTANCE);

    private final EurekaHttpClient writeRequestHandler = mock(EurekaHttpClient.class);
    private final EurekaHttpClient readRequestHandler = mock(EurekaHttpClient.class);

    private EurekaClientConfig clientConfig;
    private EurekaTransportConfig transportConfig;

    private SimpleEurekaHttpServer writeServer;
    private SimpleEurekaHttpServer readServer;

    private ClusterResolver<EurekaEndpoint> clusterResolver;
    private EndpointRandomizer randomizer;
    private EurekaHttpClientFactory clientFactory;

    private String readServerURI;

    private final InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, 1).build();

    @Before
    public void setUp() throws IOException {
        clientConfig = mock(EurekaClientConfig.class);
        transportConfig = mock(EurekaTransportConfig.class);
        randomizer = ResolverUtils::randomize;

        when(clientConfig.getEurekaServerTotalConnectionsPerHost()).thenReturn(10);
        when(clientConfig.getEurekaServerTotalConnections()).thenReturn(10);
        when(transportConfig.getSessionedClientReconnectIntervalSeconds()).thenReturn(10);

        writeServer = new SimpleEurekaHttpServer(writeRequestHandler);
        clusterResolver = new StaticClusterResolver<EurekaEndpoint>("regionA", new DefaultEndpoint("localhost", writeServer.getServerPort(), false, "/v2/"));

        readServer = new SimpleEurekaHttpServer(readRequestHandler);
        readServerURI = "http://localhost:" + readServer.getServerPort();

        clientFactory = EurekaHttpClients.canonicalClientFactory(
                "test",
                transportConfig,
                clusterResolver,
                new Jersey1TransportClientFactories().newTransportClientFactory(
                        clientConfig,
                        Collections.<ClientFilter>emptyList(),
                        applicationInfoManager.getInfo()
                ));
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
                anEurekaHttpResponse(302, Applications.class).headers("Location", readServerURI + "/v2/apps").build()
        );
        when(readRequestHandler.getApplications()).thenReturn(
                anEurekaHttpResponse(200, apps).headers(HttpHeaders.CONTENT_TYPE, "application/json").build()
        );

        EurekaHttpClient eurekaHttpClient = clientFactory.newClient();
        EurekaHttpResponse<Applications> result = eurekaHttpClient.getApplications();

        assertThat(result.getStatusCode(), is(equalTo(200)));
        assertThat(EurekaEntityComparators.equal(result.getEntity(), apps), is(true));
    }

    @Test
    public void testCompositeBootstrapResolver() throws Exception {
        Applications applications = InstanceInfoGenerator.newBuilder(5, "eurekaWrite", "someOther").build().toApplications();
        Applications applications2 = InstanceInfoGenerator.newBuilder(2, "eurekaWrite", "someOther").build().toApplications();
        String vipAddress = applications.getRegisteredApplications("eurekaWrite").getInstances().get(0).getVIPAddress();

        // setup client config to use fixed root ips for testing
        when(clientConfig.shouldUseDnsForFetchingServiceUrls()).thenReturn(false);
        when(clientConfig.getEurekaServerServiceUrls(anyString())).thenReturn(Arrays.asList("http://foo:0"));  // can use anything here
        when(clientConfig.getRegion()).thenReturn("us-east-1");

        when(transportConfig.getWriteClusterVip()).thenReturn(vipAddress);
        when(transportConfig.getAsyncExecutorThreadPoolSize()).thenReturn(4);
        when(transportConfig.getAsyncResolverRefreshIntervalMs()).thenReturn(400);
        when(transportConfig.getAsyncResolverWarmUpTimeoutMs()).thenReturn(400);

        ApplicationsResolver.ApplicationsSource applicationsSource = mock(ApplicationsResolver.ApplicationsSource.class);
        when(applicationsSource.getApplications(anyInt(), eq(TimeUnit.SECONDS)))
                .thenReturn(null)  // first time
                .thenReturn(applications)  // second time
                .thenReturn(null);  // subsequent times

        EurekaHttpClient mockHttpClient = mock(EurekaHttpClient.class);
        when(mockHttpClient.getVip(eq(vipAddress)))
                .thenReturn(anEurekaHttpResponse(200, applications).build())
                .thenReturn(anEurekaHttpResponse(200, applications2).build());  // contains diff number of servers

        TransportClientFactory transportClientFactory = mock(TransportClientFactory.class);
        when(transportClientFactory.newClient(any(EurekaEndpoint.class))).thenReturn(mockHttpClient);

        ClosableResolver<AwsEndpoint> resolver = null;
        try {
            resolver = EurekaHttpClients.compositeBootstrapResolver(
                    clientConfig,
                    transportConfig,
                    transportClientFactory,
                    applicationInfoManager.getInfo(),
                    applicationsSource,
                    randomizer
            );

            List endpoints = resolver.getClusterEndpoints();
            assertThat(endpoints.size(), equalTo(applications.getInstancesByVirtualHostName(vipAddress).size()));

            // wait for the second cycle that hits the app source
            verify(applicationsSource, timeout(3000).times(2)).getApplications(anyInt(), eq(TimeUnit.SECONDS));
            endpoints = resolver.getClusterEndpoints();
            assertThat(endpoints.size(), equalTo(applications.getInstancesByVirtualHostName(vipAddress).size()));

            // wait for the third cycle that triggers the mock http client (which is the third resolver cycle)
            // for the third cycle we have mocked the application resolver to return null data so should fall back
            // to calling the remote resolver again (which should return applications2)
            verify(mockHttpClient, timeout(3000).times(3)).getVip(anyString());
            endpoints = resolver.getClusterEndpoints();
            assertThat(endpoints.size(), equalTo(applications2.getInstancesByVirtualHostName(vipAddress).size()));
        } finally {
            if (resolver != null) {
                resolver.shutdown();
            }
        }
    }

    @Test
    public void testCanonicalResolver() throws Exception {
        when(clientConfig.getEurekaServerURLContext()).thenReturn("context");
        when(clientConfig.getRegion()).thenReturn("region");

        when(transportConfig.getAsyncExecutorThreadPoolSize()).thenReturn(3);
        when(transportConfig.getAsyncResolverRefreshIntervalMs()).thenReturn(400);
        when(transportConfig.getAsyncResolverWarmUpTimeoutMs()).thenReturn(400);

        Applications applications = InstanceInfoGenerator.newBuilder(5, "eurekaRead", "someOther").build().toApplications();
        String vipAddress = applications.getRegisteredApplications("eurekaRead").getInstances().get(0).getVIPAddress();

        ApplicationsResolver.ApplicationsSource applicationsSource = mock(ApplicationsResolver.ApplicationsSource.class);
        when(applicationsSource.getApplications(anyInt(), eq(TimeUnit.SECONDS)))
                .thenReturn(null)  // first time
                .thenReturn(applications);  // subsequent times

        EurekaHttpClientFactory remoteResolverClientFactory = mock(EurekaHttpClientFactory.class);
        EurekaHttpClient httpClient = mock(EurekaHttpClient.class);
        when(remoteResolverClientFactory.newClient()).thenReturn(httpClient);
        when(httpClient.getVip(vipAddress)).thenReturn(EurekaHttpResponse.anEurekaHttpResponse(200, applications).build());

        EurekaHttpResolver remoteResolver = spy(new TestEurekaHttpResolver(clientConfig, transportConfig, remoteResolverClientFactory, vipAddress));
        when(transportConfig.getReadClusterVip()).thenReturn(vipAddress);

        ApplicationsResolver localResolver = spy(new ApplicationsResolver(
                clientConfig, transportConfig, applicationsSource, transportConfig.getReadClusterVip()));

        ClosableResolver resolver = null;
        try {
            resolver = EurekaHttpClients.compositeQueryResolver(
                    remoteResolver,
                    localResolver,
                    clientConfig,
                    transportConfig,
                    applicationInfoManager.getInfo(),
                    randomizer
            );

            List endpoints = resolver.getClusterEndpoints();
            assertThat(endpoints.size(), equalTo(applications.getInstancesByVirtualHostName(vipAddress).size()));
            verify(remoteResolver, times(1)).getClusterEndpoints();
            verify(localResolver, times(1)).getClusterEndpoints();

            // wait for the second cycle that hits the app source
            verify(applicationsSource, timeout(3000).times(2)).getApplications(anyInt(), eq(TimeUnit.SECONDS));
            endpoints = resolver.getClusterEndpoints();
            assertThat(endpoints.size(), equalTo(applications.getInstancesByVirtualHostName(vipAddress).size()));

            verify(remoteResolver, times(1)).getClusterEndpoints();
            verify(localResolver, times(2)).getClusterEndpoints();
        } finally {
            if (resolver != null) {
                resolver.shutdown();
            }
        }
    }

    @Test
    public void testAddingAdditionalFilters() throws Exception {
        TestFilter testFilter = new TestFilter();
        Collection<ClientFilter> additionalFilters = Arrays.<ClientFilter>asList(testFilter);

        TransportClientFactory transportClientFactory = new Jersey1TransportClientFactories().newTransportClientFactory(
                clientConfig,
                additionalFilters,
                MY_INSTANCE
        );

        EurekaHttpClient client = transportClientFactory.newClient(clusterResolver.getClusterEndpoints().get(0));
        client.getApplication("foo");

        assertThat(testFilter.await(30, TimeUnit.SECONDS), is(true));
    }

    private static class TestFilter extends ClientFilter {

        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public ClientResponse handle(ClientRequest cr) throws ClientHandlerException {
            latch.countDown();
            return mock(ClientResponse.class);
        }

        public boolean await(long timeout, TimeUnit unit) throws Exception {
            return latch.await(timeout, unit);
        }
    }
}
package com.netflix.discovery.shared.resolver.aws;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class EurekaHttpResolverTest {

    private final EurekaClientConfig clientConfig = mock(EurekaClientConfig.class);
    private final EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);
    private final EurekaHttpClientFactory clientFactory = mock(EurekaHttpClientFactory.class);
    private final EurekaHttpClient httpClient = mock(EurekaHttpClient.class);

    private Applications applications;
    private String vipAddress;
    private EurekaHttpResolver resolver;

    @Before
    public void setUp() {
        when(clientConfig.getEurekaServerURLContext()).thenReturn("context");
        when(clientConfig.getRegion()).thenReturn("region");

        applications = InstanceInfoGenerator.newBuilder(5, "eurekaRead", "someOther").build().toApplications();
        vipAddress = applications.getRegisteredApplications("eurekaRead").getInstances().get(0).getVIPAddress();

        when(clientFactory.newClient()).thenReturn(httpClient);
        when(httpClient.getVip(vipAddress)).thenReturn(EurekaHttpResponse.anEurekaHttpResponse(200, applications).build());

        resolver = new EurekaHttpResolver(clientConfig, transportConfig, clientFactory, vipAddress);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testHappyCase() {
        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.size(), equalTo(applications.getInstancesByVirtualHostName(vipAddress).size()));

        verify(httpClient, times(1)).shutdown();
    }

    @Test
    public void testNoValidDataFromRemoteServer() {
        Applications newApplications = new Applications();
        for (Application application : applications.getRegisteredApplications()) {
            if (!application.getInstances().get(0).getVIPAddress().equals(vipAddress)) {
                newApplications.addApplication(application);
            }
        }
        when(httpClient.getVip(vipAddress)).thenReturn(EurekaHttpResponse.anEurekaHttpResponse(200, newApplications).build());

        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.isEmpty(), is(true));

        verify(httpClient, times(1)).shutdown();
    }

    @Test
    public void testErrorResponseFromRemoteServer() {
        when(httpClient.getVip(vipAddress)).thenReturn(EurekaHttpResponse.anEurekaHttpResponse(500, (Applications)null).build());

        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.isEmpty(), is(true));

        verify(httpClient, times(1)).shutdown();
    }
}

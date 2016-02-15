package com.netflix.discovery.shared.resolver.aws;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.aws.ApplicationsResolver.ApplicationsSource;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ApplicationsResolverTest {

    private final EurekaClientConfig clientConfig = mock(EurekaClientConfig.class);
    private final EurekaTransportConfig transportConfig = mock(EurekaTransportConfig.class);

    private final ApplicationsSource applicationsSource = mock(ApplicationsSource.class);
    private Applications applications;
    private String vipAddress;
    private ApplicationsResolver resolver;

    @Before
    public void setUp() {
        when(clientConfig.getEurekaServerURLContext()).thenReturn("context");
        when(clientConfig.getRegion()).thenReturn("region");
        when(transportConfig.getApplicationsResolverDataStalenessThresholdSeconds()).thenReturn(1);

        applications = InstanceInfoGenerator.newBuilder(5, "eurekaRead", "someOther").build().toApplications();
        vipAddress = applications.getRegisteredApplications("eurekaRead").getInstances().get(0).getVIPAddress();
        when(transportConfig.getReadClusterVip()).thenReturn(vipAddress);

        resolver = new ApplicationsResolver(
                clientConfig,
                transportConfig,
                applicationsSource,
                transportConfig.getReadClusterVip()
        );
    }

    @Test
    public void testHappyCase() {
        when(applicationsSource.getApplications(anyInt(), eq(TimeUnit.SECONDS))).thenReturn(applications);

        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.size(), equalTo(applications.getInstancesByVirtualHostName(vipAddress).size()));
    }

    @Test
    public void testVipDoesNotExist() {
        vipAddress = "doNotExist";
        when(transportConfig.getReadClusterVip()).thenReturn(vipAddress);

        resolver = new ApplicationsResolver(  // recreate the resolver as desired config behaviour has changed
                clientConfig,
                transportConfig,
                applicationsSource,
                transportConfig.getReadClusterVip()
        );

        when(applicationsSource.getApplications(anyInt(), eq(TimeUnit.SECONDS))).thenReturn(applications);

        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.isEmpty(), is(true));
    }

    @Test
    public void testStaleData() {
        when(applicationsSource.getApplications(anyInt(), eq(TimeUnit.SECONDS))).thenReturn(null);  // stale contract

        List<AwsEndpoint> endpoints = resolver.getClusterEndpoints();
        assertThat(endpoints.isEmpty(), is(true));
    }
}

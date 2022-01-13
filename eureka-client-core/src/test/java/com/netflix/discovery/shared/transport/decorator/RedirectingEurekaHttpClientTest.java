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

package com.netflix.discovery.shared.transport.decorator;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.dns.DnsService;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.TransportException;
import org.junit.Test;
import org.mockito.Matchers;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class RedirectingEurekaHttpClientTest {

    private static final String SERVICE_URL = "http://mydiscovery.test";

    private final TransportClientFactory factory = mock(TransportClientFactory.class);

    private final EurekaHttpClient sourceClient = mock(EurekaHttpClient.class);
    private final EurekaHttpClient redirectedClient = mock(EurekaHttpClient.class);
    private final DnsService dnsService = mock(DnsService.class);

    public void setupRedirect() {
        when(factory.newClient(Matchers.<EurekaEndpoint>anyVararg())).thenReturn(sourceClient, redirectedClient);
        when(sourceClient.getApplications()).thenReturn(
                anEurekaHttpResponse(302, Applications.class)
                        .headers(HttpHeaders.LOCATION, "http://another.discovery.test/eureka/v2/apps")
                        .build()
        );
        when(dnsService.resolveIp("another.discovery.test")).thenReturn("192.168.0.1");
        when(redirectedClient.getApplications()).thenReturn(
                anEurekaHttpResponse(200, new Applications()).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
    }

    @Test
    public void testNonRedirectedRequestsAreServedByFirstClient() throws Exception {
        when(factory.newClient(Matchers.<EurekaEndpoint>anyVararg())).thenReturn(sourceClient);
        when(sourceClient.getApplications()).thenReturn(
                anEurekaHttpResponse(200, new Applications()).type(MediaType.APPLICATION_JSON_TYPE).build()
        );

        RedirectingEurekaHttpClient httpClient = new RedirectingEurekaHttpClient(SERVICE_URL, factory, dnsService);
        httpClient.getApplications();

        verify(factory, times(1)).newClient(Matchers.<EurekaEndpoint>anyVararg());
        verify(sourceClient, times(1)).getApplications();
    }

    @Test
    public void testRedirectsAreFollowedAndClientIsPinnedToTheLastServer() throws Exception {
        setupRedirect();

        RedirectingEurekaHttpClient httpClient = new RedirectingEurekaHttpClient(SERVICE_URL, factory, dnsService);

        // First call pins client to resolved IP
        httpClient.getApplications();

        verify(factory, times(2)).newClient(Matchers.<EurekaEndpoint>anyVararg());
        verify(sourceClient, times(1)).getApplications();
        verify(dnsService, times(1)).resolveIp("another.discovery.test");
        verify(redirectedClient, times(1)).getApplications();

        // Second call goes straight to the same address
        httpClient.getApplications();

        verify(factory, times(2)).newClient(Matchers.<EurekaEndpoint>anyVararg());
        verify(sourceClient, times(1)).getApplications();
        verify(dnsService, times(1)).resolveIp("another.discovery.test");
        verify(redirectedClient, times(2)).getApplications();
    }

    @Test
    public void testOnConnectionErrorPinnedClientIsDestroyed() throws Exception {
        setupRedirect();

        RedirectingEurekaHttpClient httpClient = new RedirectingEurekaHttpClient(SERVICE_URL, factory, dnsService);

        // First call pins client to resolved IP
        httpClient.getApplications();
        verify(redirectedClient, times(1)).getApplications();

        // Trigger connection error
        when(redirectedClient.getApplications()).thenThrow(new TransportException("simulated network error"));
        try {
            httpClient.getApplications();
            fail("Expected transport error");
        } catch (Exception ignored) {
        }

        // Subsequent connection shall create new httpClient
        reset(factory, sourceClient, dnsService, redirectedClient);
        setupRedirect();

        httpClient.getApplications();

        verify(factory, times(2)).newClient(Matchers.<EurekaEndpoint>anyVararg());
        verify(sourceClient, times(1)).getApplications();
        verify(dnsService, times(1)).resolveIp("another.discovery.test");
        verify(redirectedClient, times(1)).getApplications();
    }
}
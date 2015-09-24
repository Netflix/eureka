/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka.util.EurekaMonitors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class RateLimitingFilterTest {

    private static final String FULL_FETCH = "base/apps";
    private static final String DELTA_FETCH = "base/apps/delta";
    private static final String APP_FETCH = "base/apps/myAppId";

    private static final String CUSTOM_CLIENT = "CustomClient";
    private static final String PYTHON_CLIENT = "PythonClient";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;

    @Before
    public void setUp() throws Exception {
        RateLimitingFilter.reset();

        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.privilegedClients", PYTHON_CLIENT);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.enabled", true);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.burstSize", 2);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.registryFetchAverageRate", 1);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.fullFetchAverageRate", 1);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.throttleStandardClients", false);

        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(new MyDataCenterInstanceConfig());
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        EurekaServerContext mockServer = mock(EurekaServerContext.class);
        when(mockServer.getServerConfig()).thenReturn(config);

        filter = new RateLimitingFilter(mockServer);
    }

    @Test
    public void testPrivilegedClientAlwaysServed() throws Exception {
        whenRequest(FULL_FETCH, PYTHON_CLIENT);
        filter.doFilter(request, response, filterChain);

        whenRequest(DELTA_FETCH, EurekaClientIdentity.DEFAULT_CLIENT_NAME);
        filter.doFilter(request, response, filterChain);

        whenRequest(APP_FETCH, EurekaServerIdentity.DEFAULT_SERVER_NAME);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(3)).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testStandardClientsThrottlingEnforceable() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.throttleStandardClients", true);

        // Custom clients will go up to the window limit
        whenRequest(FULL_FETCH, EurekaClientIdentity.DEFAULT_CLIENT_NAME);
        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);

        // Now we hit the limit
        long rateLimiterCounter = EurekaMonitors.RATE_LIMITED.getCount();
        filter.doFilter(request, response, filterChain);

        assertEquals("Expected rate limiter counter increase", rateLimiterCounter + 1, EurekaMonitors.RATE_LIMITED.getCount());
        verify(response, times(1)).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testCustomClientShedding() throws Exception {
        // Custom clients will go up to the window limit
        whenRequest(FULL_FETCH, CUSTOM_CLIENT);
        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);

        // Now we hit the limit
        long rateLimiterCounter = EurekaMonitors.RATE_LIMITED.getCount();
        filter.doFilter(request, response, filterChain);

        assertEquals("Expected rate limiter counter increase", rateLimiterCounter + 1, EurekaMonitors.RATE_LIMITED.getCount());
        verify(response, times(1)).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testCustomClientThrottlingCandidatesCounter() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.enabled", false);

        // Custom clients will go up to the window limit
        whenRequest(FULL_FETCH, CUSTOM_CLIENT);

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);

        // Now we hit the limit
        long rateLimiterCounter = EurekaMonitors.RATE_LIMITED_CANDIDATES.getCount();
        filter.doFilter(request, response, filterChain);

        assertEquals("Expected rate limiter counter increase", rateLimiterCounter + 1, EurekaMonitors.RATE_LIMITED_CANDIDATES.getCount());
        // We just test the counter
        verify(response, times(0)).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    private void whenRequest(String path, String client) {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn(path);
        when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(client);
    }
}
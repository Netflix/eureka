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
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.config.ConfigurationManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class RateLimitingFilterTest {

    private static final String CUSTOM_CLIENT = "CustomClient";
    private static final String PYTHON_CLIENT = "PythonClient";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final RateLimitingFilter filter = new RateLimitingFilter();

    @Before
    public void setUp() throws Exception {
        RateLimitingFilter.reset();

        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.privilidgedClients", PYTHON_CLIENT);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.enabled", true);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.maxInWindow", 2);
        ConfigurationManager.getConfigInstance().setProperty("eureka.rateLimiter.windowSize", 1000);

        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(config);
    }

    @Test
    public void testPrivilidgedClientAlwaysServed() throws Exception {
        for (int i = 0; i < 2; i++) {
            when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(PYTHON_CLIENT);
            when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(EurekaClientIdentity.DEFAULT_CLIENT_NAME);
            when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(EurekaServerIdentity.DEFAULT_SERVER_NAME);
        }

        for (int i = 0; i < 6; i++) {
            filter.doFilter(request, response, filterChain);
        }
        verify(filterChain, times(6)).doFilter(request, response);
        verify(response, never()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testCustomClientShedding() throws Exception {
        // Privilidged clients will go up to the window limit
        when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(CUSTOM_CLIENT);
        when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(CUSTOM_CLIENT);

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);

        // Now we hit the limit
        when(request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY)).thenReturn(CUSTOM_CLIENT);
        filter.doFilter(request, response, filterChain);

        verify(response, times(1)).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }
}
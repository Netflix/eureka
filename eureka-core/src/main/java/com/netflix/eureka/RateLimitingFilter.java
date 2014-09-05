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

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.eureka.util.EurekaMonitors;
import com.netflix.eureka.util.RateLimiter;

/**
 * Rate limiting filter, with configurable threshold above which non-privilidged clients
 * will be dropped. This feature enables cutting off non-standard and potentially harmful clients
 * in case of system overload.
 * The client is identified by {@link AbstractEurekaIdentity#AUTH_NAME_HEADER_KEY} HTTP header
 * value. The privilidged group by default contains:
 * <ul>
 * <li>
 *     {@link EurekaClientIdentity#DEFAULT_CLIENT_NAME} - standard Java eureka-client. Applications using
 *     this client automatically belong to the privilidged group.
 * </li>
 * <li>
 *     {@link com.netflix.eureka.EurekaServerIdentity#DEFAULT_SERVER_NAME} - connections from peer Eureka servers
 *     (internal only, traffic replication)
 * </li>
 * </ul>
 *
 * This feature is not enabled by default, but can be turned on via configuration.
 *
 * <p>
 * Rate limiter implementation is based on token bucket algorithm. There are two configurable
 * parameters:
 * <ul>
 * <li>
 *     burst size - maximum number of requests allowed into the system as a burst
 * </li>
 * <li>
 *     average rate - expected number of requests per second
 * </li>
 * </ul>
 *
 * @author Tomasz Bak
 */
public class RateLimitingFilter implements Filter {

    private static final Set<String> DEFAULT_PRIVILEDGED_CLIENTS = new HashSet<String>(
            Arrays.asList(EurekaClientIdentity.DEFAULT_CLIENT_NAME, EurekaServerIdentity.DEFAULT_SERVER_NAME)
    );

    private static final RateLimiter rateLimiter = new RateLimiter();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            if (EurekaServerConfigurationManager.getInstance().getConfiguration().isRateLimiterEnabled()) {
                if (isRateLimited((HttpServletRequest) request)) {
                    EurekaMonitors.RATE_LIMITED.increment();
                    // We just count it for now.
                    // ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isRateLimited(HttpServletRequest request) {
        return !isPrivilidged(request) && isOverloaded();
    }

    private static boolean isPrivilidged(HttpServletRequest request) {
        Set<String> privilidgedClients = EurekaServerConfigurationManager.getInstance().getConfiguration().getRateLimiterPrivilidgedClients();
        String clientName = request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY);
        return privilidgedClients.contains(clientName) || DEFAULT_PRIVILEDGED_CLIENTS.contains(clientName);
    }

    private static boolean isOverloaded() {
        int maxInWindow = EurekaServerConfigurationManager.getInstance().getConfiguration().getRateLimiterBurstSize();
        int windowSize = EurekaServerConfigurationManager.getInstance().getConfiguration().getRateLimiterAverageRate();
        return !rateLimiter.acquire(maxInWindow, windowSize);
    }

    @Override
    public void destroy() {
    }

    // For testing purposes
    static void reset() {
        rateLimiter.reset();
    }
}

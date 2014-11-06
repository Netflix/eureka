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
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.eureka.util.EurekaMonitors;
import com.netflix.eureka.util.RateLimiter;

/**
 * Rate limiting filter, with configurable threshold above which non-privileged clients
 * will be dropped. This feature enables cutting off non-standard and potentially harmful clients
 * in case of system overload. Since it is critical to always allow client registrations and heartbeats into
 * the system, which at the same time are relatively cheap operations, the rate limiting is applied only to
 * full and delta registry fetches. Furthermore, since delta fetches are much smaller than full fetches,
 * and if not served my result in following full registry fetch from the client, they have relatively
 * higher priority. This is implemented by two parallel rate limiters, one for overall number of
 * full/delta fetches (higher threshold) and one for full fetches only (low threshold).
 * <p>
 * The client is identified by {@link AbstractEurekaIdentity#AUTH_NAME_HEADER_KEY} HTTP header
 * value. The privileged group by default contains:
 * <ul>
 * <li>
 *     {@link EurekaClientIdentity#DEFAULT_CLIENT_NAME} - standard Java eureka-client. Applications using
 *     this client automatically belong to the privileged group.
 * </li>
 * <li>
 *     {@link com.netflix.eureka.EurekaServerIdentity#DEFAULT_SERVER_NAME} - connections from peer Eureka servers
 *     (internal only, traffic replication)
 * </li>
 * </ul>
 * It is possible to turn off privileged client filtering via
 * {@link EurekaServerConfig#isRateLimiterThrottleStandardClients()} property.
 * <p>
 * Rate limiting is not enabled by default, but can be turned on via configuration. Even when disabled,
 * the throttling statistics are still counted, although on a separate counter, so it is possible to
 * measure the impact of this feature before activation.
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

    private static final Set<String> DEFAULT_PRIVILEGED_CLIENTS = new HashSet<String>(
            Arrays.asList(EurekaClientIdentity.DEFAULT_CLIENT_NAME, EurekaServerIdentity.DEFAULT_SERVER_NAME)
    );

    enum Target {FullFetch, DeltaFetch, Other}

    /**
     * Includes both full and delta fetches.
     */
    private static final RateLimiter registryFetchRateLimiter = new RateLimiter();

    /**
     * Only full registry fetches.
     */
    private static final RateLimiter registryFullFetchRateLimiter = new RateLimiter();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Target target = getTarget(request);
        if (target == Target.Other) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (isRateLimited(httpRequest, target)) {
            incrementStats(target);
            if (config().isRateLimiterEnabled()) {
                ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static Target getTarget(ServletRequest request) {
        Target target = Target.Other;
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            if ("GET".equals(httpRequest.getMethod())) {
                String path = httpRequest.getPathInfo();
                if (path.endsWith("/apps") || path.endsWith("/apps/")) {
                    target = Target.FullFetch;
                } else if (path.endsWith("/apps/delta") || path.endsWith("/apps/delta/")) {
                    target = Target.DeltaFetch;
                }
            }
        }
        return target;
    }

    private static boolean isRateLimited(HttpServletRequest request, Target target) {
        return !isPrivileged(request) && isOverloaded(target);
    }

    private static boolean isPrivileged(HttpServletRequest request) {
        if (config().isRateLimiterThrottleStandardClients()) {
            return false;
        }
        Set<String> privilegedClients = config().getRateLimiterPrivilegedClients();
        String clientName = request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY);
        return privilegedClients.contains(clientName) || DEFAULT_PRIVILEGED_CLIENTS.contains(clientName);
    }

    private static boolean isOverloaded(Target target) {
        int maxInWindow = config().getRateLimiterBurstSize();
        int fetchWindowSize = config().getRateLimiterRegistryFetchAverageRate();
        boolean overloaded = !registryFetchRateLimiter.acquire(maxInWindow, fetchWindowSize);

        if (target == Target.FullFetch) {
            int fullFetchWindowSize = config().getRateLimiterFullFetchAverageRate();
            overloaded |= !registryFullFetchRateLimiter.acquire(maxInWindow, fullFetchWindowSize);
        }
        return overloaded;
    }

    private static void incrementStats(Target target) {
        if (config().isRateLimiterEnabled()) {
            EurekaMonitors.RATE_LIMITED.increment();
            if (target == Target.FullFetch) {
                EurekaMonitors.RATE_LIMITED_FULL_FETCH.increment();
            }
        } else {
            EurekaMonitors.RATE_LIMITED_CANDIDATES.increment();
            if (target == Target.FullFetch) {
                EurekaMonitors.RATE_LIMITED_FULL_FETCH_CANDIDATES.increment();
            }
        }
    }

    private static EurekaServerConfig config() {
        return EurekaServerConfigurationManager.getInstance().getConfiguration();
    }

    @Override
    public void destroy() {
    }

    // For testing purposes
    static void reset() {
        registryFetchRateLimiter.reset();
        registryFullFetchRateLimiter.reset();
    }
}

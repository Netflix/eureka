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

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.discovery.DnsResolver;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.TransportException;
import com.netflix.discovery.shared.transport.TransportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link EurekaHttpClient} that follows redirect links, and executes the requests against
 * the finally resolved endpoint.
 * If registration and query requests must handled separately, two different instances shall be created.
 * <h3>Thread safety</h3>
 * Methods in this class may be called concurrently.
 *
 * @author Tomasz Bak
 */
public class RedirectingEurekaHttpClient extends EurekaHttpClientDecorator {

    private static final Logger logger = LoggerFactory.getLogger(RedirectingEurekaHttpClient.class);

    public static final int MAX_FOLLOWED_REDIRECTS = 10;
    private static final Pattern REDIRECT_PATH_REGEX = Pattern.compile("(.*/v2/)apps(/.*)?$");

    private final String serviceUrl;
    private final EurekaHttpClientFactory factory;

    private final AtomicReference<EurekaHttpClient> delegateRef = new AtomicReference<>();

    /**
     * The delegate client should pass through 3xx responses without further processing.
     */
    public RedirectingEurekaHttpClient(String serviceUrl, EurekaHttpClientFactory factory) {
        this.serviceUrl = serviceUrl;
        this.factory = factory;
    }

    @Override
    public void shutdown() {
        TransportUtils.shutdown(delegateRef.getAndSet(null));
    }

    @Override
    protected <R> EurekaHttpResponse<R> execute(RequestExecutor<R> requestExecutor) {
        AtomicReference<EurekaHttpClient> currentEurekaClientRef = new AtomicReference<>(delegateRef.get());
        if (currentEurekaClientRef.get() == null) {
            currentEurekaClientRef.set(factory.create(serviceUrl));
        }
        EurekaHttpResponse<R> httpResponse = execute(requestExecutor, currentEurekaClientRef);
        TransportUtils.replaceClient(delegateRef, currentEurekaClientRef.get());
        return httpResponse;
    }

    private <R> EurekaHttpResponse<R> execute(RequestExecutor<R> requestExecutor, AtomicReference<EurekaHttpClient> currentHttpClientRef) {
        try {
            for (int followRedirectCount = 0; followRedirectCount < MAX_FOLLOWED_REDIRECTS; followRedirectCount++) {
                EurekaHttpResponse<R> httpResponse = requestExecutor.execute(currentHttpClientRef.get());
                if (httpResponse.getStatusCode() != 302) {
                    return httpResponse;
                }

                URI targetUrl = getRedirectBaseUri(httpResponse.getLocation());
                if (targetUrl == null) {
                    throw new TransportException("Invalid redirect URL " + httpResponse.getLocation());
                }

                currentHttpClientRef.getAndSet(null).shutdown();
                currentHttpClientRef.set(factory.create(targetUrl.toString()));
            }
        } catch (Exception e) {
            TransportUtils.shutdown(currentHttpClientRef.getAndSet(null));
            throw e;
        }
        currentHttpClientRef.getAndSet(null).shutdown();
        String message = "Follow redirect limit crossed for URI " + serviceUrl;
        logger.warn(message);
        throw new TransportException(message);
    }

    private static URI getRedirectBaseUri(URI targetUrl) {
        Matcher pathMatcher = REDIRECT_PATH_REGEX.matcher(targetUrl.getPath());
        if (pathMatcher.matches()) {
            return UriBuilder.fromUri(targetUrl)
                    .host(DnsResolver.resolve(targetUrl.getHost()))
                    .replacePath(pathMatcher.group(1))
                    .replaceQuery(null)
                    .build();
        }
        logger.warn("Invalid redirect URL {}", targetUrl);
        return null;
    }
}

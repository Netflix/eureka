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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.EurekaClientNames;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.TransportException;
import com.netflix.discovery.shared.transport.TransportUtils;
import com.netflix.discovery.util.ServoUtil;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RetryableEurekaHttpClient} retries failed requests on subsequent servers in the cluster.
 * It maintains also simple quarantine list, so operations are not retried again on servers
 * that are not reachable at the moment.
 * <h3>Quarantine</h3>
 * All the servers to which communication failed are put on the quarantine list. First successful execution
 * clears this list, which makes those server eligible for serving future requests.
 * The list is also cleared once all available servers are exhausted.
 * <h3>5xx</h3>
 * If 5xx status code is returned, {@link ServerStatusEvaluator} predicate evaluates if the retries should be
 * retried on another server, or the response with this status code returned to the client.
 *
 * @author Tomasz Bak
 */
public class RetryableEurekaHttpClient extends EurekaHttpClientDecorator {

    private static final Logger logger = LoggerFactory.getLogger(RetryableEurekaHttpClient.class);

    public static final int DEFAULT_NUMBER_OF_RETRIES = 3;

    private final ClusterResolver clusterResolver;
    private final EurekaHttpClientFactory clientFactory;
    private final ServerStatusEvaluator serverStatusEvaluator;
    private final int numberOfRetries;

    private final AtomicReference<EurekaHttpClient> delegate = new AtomicReference<>();

    private final Set<EurekaEndpoint> quarantineSet = new ConcurrentSkipListSet<>();
    private final Counter retryCounter;

    public RetryableEurekaHttpClient(ClusterResolver clusterResolver,
                                     EurekaHttpClientFactory clientFactory,
                                     ServerStatusEvaluator serverStatusEvaluator,
                                     int numberOfRetries) {
        this.clusterResolver = clusterResolver;
        this.clientFactory = clientFactory;
        this.serverStatusEvaluator = serverStatusEvaluator;
        this.numberOfRetries = numberOfRetries;

        this.retryCounter = new BasicCounter(MonitorConfig.builder(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "retries").build());
        ServoUtil.register(retryCounter);
    }

    @Override
    public void shutdown() {
        TransportUtils.shutdown(delegate.get());
        ServoUtil.unregister(retryCounter);
    }

    @Override
    protected <R> EurekaHttpResponse<R> execute(RequestExecutor<R> requestExecutor) {
        List<EurekaEndpoint> candidateHosts = null;
        int endpointIdx = 0;
        for (int retry = 0; retry < numberOfRetries; retry++) {
            EurekaHttpClient currentHttpClient = delegate.get();
            EurekaEndpoint currentEndpoint = null;
            if (currentHttpClient == null) {
                if (candidateHosts == null) {
                    candidateHosts = getHostCandidates();
                    if (candidateHosts.isEmpty()) {
                        throw new TransportException("There is no known eureka server; cluster server list is empty");
                    }
                }
                if (endpointIdx >= candidateHosts.size()) {
                    throw new TransportException("Cannot execute request on any known server");
                }

                currentEndpoint = candidateHosts.get(endpointIdx++);
                currentHttpClient = clientFactory.create(currentEndpoint.getServiceUrl());
            }

            try {
                EurekaHttpResponse<R> response = requestExecutor.execute(currentHttpClient);
                if (serverStatusEvaluator.accept(response.getStatusCode(), requestExecutor.getRequestType())) {
                    delegate.set(currentHttpClient);
                    return response;
                }
                logger.warn("Request execution failure with status code {}; retrying on another server if available", response.getStatusCode());
            } catch (Exception e) {
                logger.warn("Request execution failure", e);
            }

            // Connection error or 5xx from the server that must be retried on another server
            delegate.compareAndSet(currentHttpClient, null);
            if (currentEndpoint != null) {
                quarantineSet.add(currentEndpoint);
            }
        }
        throw new TransportException("Retry limit reached; giving up on completing the request");
    }

    public static EurekaHttpClientFactory createFactory(final ClusterResolver clusterResolver,
                                                        final EurekaHttpClientFactory delegateFactory,
                                                        final ServerStatusEvaluator serverStatusEvaluator) {
        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient create(String... serviceUrls) {
                return new RetryableEurekaHttpClient(clusterResolver, delegateFactory, serverStatusEvaluator, DEFAULT_NUMBER_OF_RETRIES);
            }

            @Override
            public void shutdown() {
                delegateFactory.shutdown();
            }
        };
    }

    private List<EurekaEndpoint> getHostCandidates() {
        List<EurekaEndpoint> candidateHosts = clusterResolver.getClusterEndpoints();
        quarantineSet.retainAll(candidateHosts);

        // If all hosts are bad, we have no choice but start over again
        if (quarantineSet.size() == candidateHosts.size()) {
            quarantineSet.clear();
        } else if (!quarantineSet.isEmpty()) {
            List<EurekaEndpoint> remainingHosts = new ArrayList<>(candidateHosts.size());
            for (EurekaEndpoint endpoint : candidateHosts) {
                if (!quarantineSet.contains(endpoint)) {
                    remainingHosts.add(endpoint);
                }
            }
            candidateHosts = remainingHosts;
        }
        return candidateHosts;
    }
}

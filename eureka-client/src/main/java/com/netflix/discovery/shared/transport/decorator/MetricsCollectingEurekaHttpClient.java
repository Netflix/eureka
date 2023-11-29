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

import com.netflix.discovery.util.SpectatorUtil;
import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.Timer;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.netflix.discovery.EurekaClientNames;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient.EurekaHttpClientRequestMetrics.Status;
import com.netflix.discovery.util.ExceptionsMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class MetricsCollectingEurekaHttpClient extends EurekaHttpClientDecorator {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectingEurekaHttpClient.class);

    private final EurekaHttpClient delegate;

    private final Map<RequestType, EurekaHttpClientRequestMetrics> metricsByRequestType;
    private final ExceptionsMetric exceptionsMetric;
    private final boolean shutdownMetrics;

    public MetricsCollectingEurekaHttpClient(EurekaHttpClient delegate) {
        this(delegate, initializeMetrics(), new ExceptionsMetric(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "exceptions"), true);
    }

    private MetricsCollectingEurekaHttpClient(EurekaHttpClient delegate,
                                              Map<RequestType, EurekaHttpClientRequestMetrics> metricsByRequestType,
                                              ExceptionsMetric exceptionsMetric,
                                              boolean shutdownMetrics) {
        this.delegate = delegate;
        this.metricsByRequestType = metricsByRequestType;
        this.exceptionsMetric = exceptionsMetric;
        this.shutdownMetrics = shutdownMetrics;
    }

    @Override
    protected <R> EurekaHttpResponse<R> execute(RequestExecutor<R> requestExecutor) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(requestExecutor.getRequestType());
        long monotonicTime = SpectatorUtil.time(requestMetrics.latencyTimer);
        try {
            EurekaHttpResponse<R> httpResponse = requestExecutor.execute(delegate);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            exceptionsMetric.count(e);
            throw e;
        } finally {
            SpectatorUtil.record(requestMetrics.latencyTimer, monotonicTime);
        }
    }

    @Override
    public void shutdown() {
        if (shutdownMetrics) {
            exceptionsMetric.shutdown();
        }
    }

    public static EurekaHttpClientFactory createFactory(final EurekaHttpClientFactory delegateFactory) {
        final Map<RequestType, EurekaHttpClientRequestMetrics> metricsByRequestType = initializeMetrics();
        final ExceptionsMetric exceptionMetrics = new ExceptionsMetric(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "exceptions");
        return new EurekaHttpClientFactory() {
            @Override
            public EurekaHttpClient newClient() {
                return new MetricsCollectingEurekaHttpClient(
                        delegateFactory.newClient(),
                        metricsByRequestType,
                        exceptionMetrics,
                        false
                );
            }

            @Override
            public void shutdown() {
                exceptionMetrics.shutdown();
            }
        };
    }

    public static TransportClientFactory createFactory(final TransportClientFactory delegateFactory) {
        final Map<RequestType, EurekaHttpClientRequestMetrics> metricsByRequestType = initializeMetrics();
        final ExceptionsMetric exceptionMetrics = new ExceptionsMetric(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "exceptions");
        return new TransportClientFactory() {
            @Override
            public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
                return new MetricsCollectingEurekaHttpClient(
                        delegateFactory.newClient(endpoint),
                        metricsByRequestType,
                        exceptionMetrics,
                        false
                );
            }

            @Override
            public void shutdown() {
                exceptionMetrics.shutdown();
            }
        };
    }

    private static Map<RequestType, EurekaHttpClientRequestMetrics> initializeMetrics() {
        Map<RequestType, EurekaHttpClientRequestMetrics> result = new EnumMap<>(RequestType.class);
        try {
            for (RequestType requestType : RequestType.values()) {
                result.put(requestType, new EurekaHttpClientRequestMetrics(requestType.name()));
            }
        } catch (Exception e) {
            logger.warn("Metrics initialization failure", e);
        }
        return result;
    }

    private static Status mappedStatus(EurekaHttpResponse<?> httpResponse) {
        int category = httpResponse.getStatusCode() / 100;
        switch (category) {
            case 1:
                return Status.x100;
            case 2:
                return Status.x200;
            case 3:
                return Status.x300;
            case 4:
                return Status.x400;
            case 5:
                return Status.x500;
        }
        return Status.Unknown;
    }

    static class EurekaHttpClientRequestMetrics {

        enum Status {x100, x200, x300, x400, x500, Unknown}

        private final Timer latencyTimer;
        private final Counter connectionErrors;
        private final Map<Status, Counter> countersByStatus;

        EurekaHttpClientRequestMetrics(String resourceName) {
            this.countersByStatus = createStatusCounters(resourceName);

            latencyTimer = SpectatorUtil.timer(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "latency",
                resourceName,
                MetricsCollectingEurekaHttpClient.class);

            this.connectionErrors = SpectatorUtil.counter(
                EurekaClientNames.METRIC_TRANSPORT_PREFIX + "connectionErrors",
                resourceName,
                MetricsCollectingEurekaHttpClient.class);
        }

        private static Map<Status, Counter> createStatusCounters(String resourceName) {
            Map<Status, Counter> result = new EnumMap<>(Status.class);

            for (Status status : Status.values()) {
                Counter counter = SpectatorUtil.counter(
                        EurekaClientNames.METRIC_TRANSPORT_PREFIX + "request",
                    resourceName,
                    MetricsCollectingEurekaHttpClient.class,
                    Collections.singletonList(new BasicTag("status", status.name())));

                result.put(status, counter);
            }

            return result;
        }
    }
}

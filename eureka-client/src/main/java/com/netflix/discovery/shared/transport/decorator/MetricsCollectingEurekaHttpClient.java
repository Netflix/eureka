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

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.EurekaClientNames;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient.EurekaHttpClientRequestMetrics.Status;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.BasicCounter;
import com.netflix.servo.monitor.BasicTimer;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class MetricsCollectingEurekaHttpClient implements EurekaHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectingEurekaHttpClient.class);

    private final EurekaHttpClient delegate;

    enum RequestType {Register, Cancel, SendHeartBeat, StatusUpdate, DeleteStatusOverride, GetApplications, GetDelta, GetInstance}

    private final Map<RequestType, EurekaHttpClientRequestMetrics> metricsByRequestType;

    public MetricsCollectingEurekaHttpClient(EurekaHttpClient delegate) {
        this.delegate = delegate;
        this.metricsByRequestType = initializeMetrics();
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.Register);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<Void> httpResponse = delegate.register(info);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<Void> cancel(String appName, String id) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.Cancel);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<Void> httpResponse = delegate.cancel(appName, id);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.SendHeartBeat);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<InstanceInfo> httpResponse = delegate.sendHeartBeat(appName, id, info, overriddenStatus);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.StatusUpdate);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<Void> httpResponse = delegate.statusUpdate(appName, id, newStatus, info);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.DeleteStatusOverride);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<Void> httpResponse = delegate.deleteStatusOverride(appName, id, info);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications() {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.GetApplications);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<Applications> httpResponse = delegate.getApplications();
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta() {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.GetDelta);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<Applications> httpResponse = delegate.getDelta();
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id) {
        EurekaHttpClientRequestMetrics requestMetrics = metricsByRequestType.get(RequestType.GetInstance);
        Stopwatch stopwatch = requestMetrics.latencyTimer.start();
        try {
            EurekaHttpResponse<InstanceInfo> httpResponse = delegate.getInstance(appName, id);
            requestMetrics.countersByStatus.get(mappedStatus(httpResponse)).increment();
            return httpResponse;
        } catch (Exception e) {
            requestMetrics.connectionErrors.increment();
            throw e;
        } finally {
            stopwatch.stop();
        }
    }

    @Override
    public void shutdown() {
        for (EurekaHttpClientRequestMetrics metrics : metricsByRequestType.values()) {
            metrics.shutdown();
        }
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
                return Status.x100;
            case 3:
                return Status.x100;
            case 4:
                return Status.x100;
            case 5:
                return Status.x100;
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

            latencyTimer = new BasicTimer(
                    MonitorConfig.builder(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "latency")
                            .withTag("id", resourceName)
                            .withTag("class", MetricsCollectingEurekaHttpClient.class.getSimpleName())
                            .build(),
                    TimeUnit.MILLISECONDS
            );
            DefaultMonitorRegistry.getInstance().register(latencyTimer);

            this.connectionErrors = new BasicCounter(
                    MonitorConfig.builder(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "connectionErrors")
                            .withTag("id", resourceName)
                            .withTag("class", MetricsCollectingEurekaHttpClient.class.getSimpleName())
                            .build()
            );
            DefaultMonitorRegistry.getInstance().register(connectionErrors);
        }

        void shutdown() {
            try {
                DefaultMonitorRegistry.getInstance().unregister(latencyTimer);
                DefaultMonitorRegistry.getInstance().unregister(connectionErrors);
                for (Counter counter : countersByStatus.values()) {
                    DefaultMonitorRegistry.getInstance().unregister(counter);
                }
            } catch (Exception ignore) {
            }
        }

        private static Map<Status, Counter> createStatusCounters(String resourceName) {
            Map<Status, Counter> result = new EnumMap<>(Status.class);

            for (Status status : Status.values()) {
                BasicCounter counter = new BasicCounter(
                        MonitorConfig.builder(EurekaClientNames.METRIC_TRANSPORT_PREFIX + "request")
                                .withTag("id", resourceName)
                                .withTag("class", MetricsCollectingEurekaHttpClient.class.getSimpleName())
                                .withTag("status", status.name())
                                .build()
                );
                DefaultMonitorRegistry.getInstance().register(counter);

                result.put(status, counter);
            }

            return result;
        }
    }
}

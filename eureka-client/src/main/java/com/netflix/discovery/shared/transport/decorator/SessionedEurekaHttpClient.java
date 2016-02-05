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

import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.TransportUtils;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.EurekaClientNames.METRIC_TRANSPORT_PREFIX;

/**
 * {@link SessionedEurekaHttpClient} enforces full reconnect at a regular interval (a session), preventing
 * a client to sticking to a particular Eureka server instance forever. This in turn guarantees even
 * load distribution in case of cluster topology change.
 *
 * @author Tomasz Bak
 */
public class SessionedEurekaHttpClient extends EurekaHttpClientDecorator {
    private static final Logger logger = LoggerFactory.getLogger(SessionedEurekaHttpClient.class);

    private final Random random = new Random();

    private final String name;
    private final EurekaHttpClientFactory clientFactory;
    private final long sessionDurationMs;
    private volatile long currentSessionDurationMs;

    private volatile long lastReconnectTimeStamp = -1;
    private final AtomicReference<EurekaHttpClient> eurekaHttpClientRef = new AtomicReference<>();

    public SessionedEurekaHttpClient(String name, EurekaHttpClientFactory clientFactory, long sessionDurationMs) {
        this.name = name;
        this.clientFactory = clientFactory;
        this.sessionDurationMs = sessionDurationMs;
        this.currentSessionDurationMs = randomizeSessionDuration(sessionDurationMs);
        Monitors.registerObject(name, this);
    }

    @Override
    protected <R> EurekaHttpResponse<R> execute(RequestExecutor<R> requestExecutor) {
        long now = System.currentTimeMillis();
        long delay = now - lastReconnectTimeStamp;
        if (delay >= currentSessionDurationMs) {
            logger.debug("Ending a session and starting anew");
            lastReconnectTimeStamp = now;
            currentSessionDurationMs = randomizeSessionDuration(sessionDurationMs);
            TransportUtils.shutdown(eurekaHttpClientRef.getAndSet(null));
        }

        EurekaHttpClient eurekaHttpClient = eurekaHttpClientRef.get();
        if (eurekaHttpClient == null) {
            eurekaHttpClient = TransportUtils.getOrSetAnotherClient(eurekaHttpClientRef, clientFactory.newClient());
        }
        return requestExecutor.execute(eurekaHttpClient);
    }

    @Override
    public void shutdown() {
        if(Monitors.isObjectRegistered(name, this)) {
            Monitors.unregisterObject(name, this);
        }
        TransportUtils.shutdown(eurekaHttpClientRef.getAndSet(null));
    }

    /**
     * @return a randomized sessionDuration in ms calculated as +/- an additional amount in [0, sessionDurationMs/2]
     */
    protected long randomizeSessionDuration(long sessionDurationMs) {
        long delta = (long) (sessionDurationMs * (random.nextDouble() - 0.5));
        return sessionDurationMs + delta;
    }

    @Monitor(name = METRIC_TRANSPORT_PREFIX + "currentSessionDuration",
            description = "Duration of the current session", type = DataSourceType.GAUGE)
    public long getCurrentSessionDuration() {
        return lastReconnectTimeStamp < 0 ? 0 : System.currentTimeMillis() - lastReconnectTimeStamp;
    }
}

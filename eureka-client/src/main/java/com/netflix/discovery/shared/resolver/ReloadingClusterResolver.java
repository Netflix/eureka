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

package com.netflix.discovery.shared.resolver;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.EurekaClientNames.METRIC_RESOLVER_PREFIX;

/**
 * A cluster resolver implementation that periodically creates a new {@link ClusterResolver} instance that
 * swaps the previous value. If the new resolver cannot be created or contains empty server list, the previous
 * one is used. Failed requests are retried using exponential back-off strategy.
 * <br/>
 * It provides more insight in form of additional logging and metrics, as it is supposed to be used as a top
 * level resolver.
 *
 * @author Tomasz Bak
 */
public class ReloadingClusterResolver<T extends EurekaEndpoint> implements ClusterResolver<T> {

    private static final Logger logger = LoggerFactory.getLogger(ReloadingClusterResolver.class);

    private static final long MAX_RELOAD_INTERVAL_MULTIPLIER = 5;

    private final ClusterResolverFactory<T> factory;
    private final long reloadIntervalMs;
    private final long maxReloadIntervalMs;

    private final AtomicReference<ClusterResolver<T>> delegateRef;

    private volatile long lastUpdateTime;
    private volatile long currentReloadIntervalMs;

    // Metric timestamp, tracking last time when data were effectively changed.
    private volatile long lastReloadTimestamp = -1;

    public ReloadingClusterResolver(final ClusterResolverFactory<T> factory, final long reloadIntervalMs) {
        this.factory = factory;
        this.reloadIntervalMs = reloadIntervalMs;
        this.maxReloadIntervalMs = MAX_RELOAD_INTERVAL_MULTIPLIER * reloadIntervalMs;
        this.delegateRef = new AtomicReference<>(factory.createClusterResolver());

        this.lastUpdateTime = System.currentTimeMillis();
        this.currentReloadIntervalMs = reloadIntervalMs;

        List<T> clusterEndpoints = delegateRef.get().getClusterEndpoints();
        if (clusterEndpoints.isEmpty()) {
            logger.error("Empty Eureka server endpoint list during initialization process");
            throw new ClusterResolverException("Resolved to an empty endpoint list");
        }

        if (logger.isInfoEnabled()) {
            logger.info("Initiated with delegate resolver of type {}; next reload in {}[sec]. Loaded endpoints={}",
                    delegateRef.get().getClass(), currentReloadIntervalMs / 1000, clusterEndpoints);
        }

        try {
            Monitors.registerObject(this);
        } catch (Throwable e) {
            logger.warn("Cannot register metrics", e);
        }
    }

    @Override
    public String getRegion() {
        ClusterResolver delegate = delegateRef.get();
        return delegate == null ? null : delegate.getRegion();
    }

    @Override
    public List<T> getClusterEndpoints() {
        long expiryTime = lastUpdateTime + currentReloadIntervalMs;
        if (expiryTime <= System.currentTimeMillis()) {
            try {
                ClusterResolver<T> newDelegate = reload();

                this.lastUpdateTime = System.currentTimeMillis();
                this.currentReloadIntervalMs = reloadIntervalMs;

                if (newDelegate != null) {
                    delegateRef.set(newDelegate);
                    lastReloadTimestamp = System.currentTimeMillis();
                    if (logger.isInfoEnabled()) {
                        logger.info("Reload endpoints differ from the original list; next reload in {}[sec], Loaded endpoints={}",
                                currentReloadIntervalMs / 1000, newDelegate.getClusterEndpoints());
                    }
                }
            } catch (Exception e) {
                this.currentReloadIntervalMs = Math.min(maxReloadIntervalMs, currentReloadIntervalMs * 2);
                logger.warn("Cluster resolve error; keeping the current Eureka endpoints; next reload in "
                        + "{}[sec]", currentReloadIntervalMs / 1000, e);
            }
        }
        return delegateRef.get().getClusterEndpoints();
    }

    private ClusterResolver<T> reload() {
        ClusterResolver<T> newDelegate = factory.createClusterResolver();
        List<T> newEndpoints = newDelegate.getClusterEndpoints();

        if (newEndpoints.isEmpty()) {
            logger.info("Tried to reload but empty endpoint list returned; keeping the current endpoints");
            return null;
        }

        // If no change in the server pool, shutdown the new delegate
        if (ResolverUtils.identical(delegateRef.get().getClusterEndpoints(), newEndpoints)) {
            logger.debug("Loaded cluster server list identical to the current one; no update required");
            return null;
        }

        return newDelegate;
    }

    @Monitor(name = METRIC_RESOLVER_PREFIX + "lastReloadTimestamp",
            description = "How much time has passed from last successful cluster configuration resolve", type = DataSourceType.GAUGE)
    public long getLastReloadTimestamp() {
        return lastReloadTimestamp < 0 ? 0 : System.currentTimeMillis() - lastReloadTimestamp;
    }
}

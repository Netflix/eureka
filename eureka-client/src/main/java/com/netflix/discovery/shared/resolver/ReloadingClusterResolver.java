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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A cluster resolver implementation that periodically creates a new {@link ClusterResolver} instance that
 * swaps the previous value.
 *
 * @author Tomasz Bak
 */
public class ReloadingClusterResolver implements ClusterResolver {

    private static final Logger logger = LoggerFactory.getLogger(ReloadingClusterResolver.class);

    private static final long MAX_RELOAD_INTERVAL_MULTIPLIER = 5;

    private final ClusterResolverFactory factory;
    private final long reloadIntervalMs;
    private final long maxReloadIntervalMs;

    private final AtomicReference<ClusterResolver> delegateRef;

    private volatile long lastUpdateTime;
    private volatile long currentReloadIntervalMs;

    public ReloadingClusterResolver(final ClusterResolverFactory factory, final long reloadIntervalMs) {
        this.factory = factory;
        this.reloadIntervalMs = reloadIntervalMs;
        this.maxReloadIntervalMs = MAX_RELOAD_INTERVAL_MULTIPLIER * reloadIntervalMs;
        this.delegateRef = new AtomicReference<>(factory.createClusterResolver());

        this.lastUpdateTime = System.currentTimeMillis();
        this.currentReloadIntervalMs = reloadIntervalMs;
    }

    @Override
    public List<EurekaEndpoint> getClusterEndpoints() {
        long expiryTime = lastUpdateTime + currentReloadIntervalMs;
        if (expiryTime <= System.currentTimeMillis()) {
            try {
                ClusterResolver newDelegate = reload();
                if (newDelegate != null) {
                    shutdownDelegate(delegateRef.getAndSet(newDelegate));
                }
                this.lastUpdateTime = System.currentTimeMillis();
                this.currentReloadIntervalMs = reloadIntervalMs;
            } catch (Exception e) {
                logger.warn("Cluster resolve error; keeping the current Eureka endpoints", e);
                this.currentReloadIntervalMs = Math.min(maxReloadIntervalMs, currentReloadIntervalMs * 2);
            }
        }
        return delegateRef.get().getClusterEndpoints();
    }

    @Override
    public void shutdown() {
        shutdownDelegate(delegateRef.get());
    }

    private ClusterResolver reload() {
        ClusterResolver newDelegate = null;
        List<EurekaEndpoint> newEndpoints;
        try {
            newDelegate = factory.createClusterResolver();
            newEndpoints = newDelegate.getClusterEndpoints();
        } catch (Exception e) {
            shutdownDelegate(newDelegate);
            throw e;
        }

        // If no change in the server pool, shutdown the new delegate
        if (ResolverUtils.identical(delegateRef.get().getClusterEndpoints(), newEndpoints)) {
            logger.debug("Loaded cluster server list identical to the current one; no update required");
            shutdownDelegate(newDelegate);
            return null;
        }
        return newDelegate;
    }

    private static void shutdownDelegate(ClusterResolver newDelegate) {
        try {
            newDelegate.shutdown();
        } catch (Throwable e) {
            logger.info("ClusterResolver shutdown failure", e);
        }
    }
}

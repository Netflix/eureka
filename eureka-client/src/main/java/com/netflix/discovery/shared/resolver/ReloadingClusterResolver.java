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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
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

    private final AtomicReference<ClusterResolver> delegateRef;

    private final Runnable reloadTask;
    private final ScheduledExecutorService executorService;

    public ReloadingClusterResolver(final ClusterResolverFactory factory, final long reloadIntervalMs) {
        this.delegateRef = new AtomicReference<>(factory.createClusterResolver());
        this.executorService = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, ReloadingClusterResolver.class.getSimpleName());
                    }
                }
        );
        this.reloadTask = new Runnable() {
            @Override
            public void run() {
                try {
                    execute();
                } catch (Throwable e) {
                    logger.warn("Error during cluster server list update; continue to use the current one", e);
                }
                if (!executorService.isShutdown()) {
                    executorService.schedule(reloadTask, reloadIntervalMs, TimeUnit.MILLISECONDS);
                }
            }

            private void execute() {
                ClusterResolver newDelegate = factory.createClusterResolver();

                // If no change in the server pool, shutdown the new delegate
                if (ResolverUtils.identical(delegateRef.get(), newDelegate)) {
                    logger.debug("Loaded cluster server list identical to the current one; no update required");
                    shutdownDelegate(newDelegate);
                } else {
                    shutdownDelegate(delegateRef.getAndSet(newDelegate));
                }
            }
        };
        this.executorService.schedule(reloadTask, reloadIntervalMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public List<EurekaEndpoint> getClusterServers() {
        return delegateRef.get().getClusterServers();
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    private static void shutdownDelegate(ClusterResolver newDelegate) {
        try {
            newDelegate.shutdown();
        } catch (Throwable e) {
            logger.info("ClusterResolver shutdown failure", e);
        }
    }
}

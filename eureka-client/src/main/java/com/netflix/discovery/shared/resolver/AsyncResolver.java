package com.netflix.discovery.shared.resolver;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.discovery.TimedSupervisorTask;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An async resolver that keeps a cached version of the endpoint list value for gets, and updates this cache
 * periodically in a different thread.
 *
 * @author David Liu
 */
public class AsyncResolver<T extends EurekaEndpoint> implements ClosableResolver<T> {
    private static final Logger logger = LoggerFactory.getLogger(AsyncResolver.class);

    // Note that warm up is best effort. If the resolver is accessed by multiple threads pre warmup, only the first
    // thread will block for the warmup (up to the configurable timeout).
    private final AtomicBoolean warmedUp = new AtomicBoolean(false);

    private final EurekaTransportConfig transportConfig;
    private final ClusterResolver<T> delegate;
    private final ScheduledExecutorService executorService;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final TimedSupervisorTask backgroundTask;
    private final AtomicReference<List<T>> resultsRef;

    public AsyncResolver(EurekaTransportConfig transportConfig,
                         ClusterResolver<T> delegate) {
        this.transportConfig = transportConfig;
        this.delegate = delegate;
        this.executorService = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder()
                        .setNameFormat("AsyncResolver-%d")
                        .setDaemon(true)
                        .build());

        this.threadPoolExecutor = new ThreadPoolExecutor(
                1, transportConfig.getAsyncExecutorThreadPoolSize(), 0, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());  // use direct handoff

        this.backgroundTask = new TimedSupervisorTask(
                this.getClass().getSimpleName(),
                executorService,
                threadPoolExecutor,
                transportConfig.getAsyncResolverRefreshIntervalMs(),
                TimeUnit.MILLISECONDS,
                5,
                updateTask
        );

        this.resultsRef = new AtomicReference<>(Collections.<T>emptyList());
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
        threadPoolExecutor.shutdown();
        backgroundTask.cancel();
    }


    @Override
    public String getRegion() {
        return delegate.getRegion();
    }

    @Override
    public List<T> getClusterEndpoints() {
        if (warmedUp.compareAndSet(false, true)) {
            doWarmUp();
            executorService.schedule(
                    backgroundTask, transportConfig.getAsyncResolverRefreshIntervalMs(), TimeUnit.MILLISECONDS);
        }
        return resultsRef.get();
    }

    /* visible for testing */ void doWarmUp() {
        Future future = null;
        try {
            future = threadPoolExecutor.submit(updateTask);
            future.get(transportConfig.getAsyncResolverWarmupTimeoutMs(), TimeUnit.MILLISECONDS);  // block until done or timeout
        } catch (Exception e) {
            logger.warn("Best effort warm up failed", e);
        } finally {
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            try {
                List<T> newList = delegate.getClusterEndpoints();
                if (newList != null) {
                    resultsRef.getAndSet(newList);
                } else {
                    logger.warn("Delegate returned null list of cluster endpoints");
                }
            } catch (Exception e) {
                logger.warn("Failed to retrieve cluster endpoints from the delegate", e);
            }
        }
    };
}

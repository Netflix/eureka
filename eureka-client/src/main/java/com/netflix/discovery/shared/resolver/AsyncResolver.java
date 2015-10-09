package com.netflix.discovery.shared.resolver;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.discovery.TimedSupervisorTask;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An async resolver that keeps a cached version of the endpoint list value for gets, and updates this cache
 * periodically in a different thread.
 *
 * @author David Liu
 */
public class AsyncResolver<T extends EurekaEndpoint> implements ClosableResolver<T> {
    private static final Logger logger = LoggerFactory.getLogger(AsyncResolver.class);

    private volatile boolean warmedUp = false;

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
                        .setNameFormat("AsynResolver-%d")
                        .setDaemon(true)
                        .build());

        this.threadPoolExecutor = new ThreadPoolExecutor(
                1, transportConfig.getAsyncExecutorThreadPoolSize(), 0, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>());  // use direct handoff

        this.resultsRef = new AtomicReference<>(Collections.<T>emptyList());
        this.backgroundTask = new TimedSupervisorTask(
                this.getClass().getSimpleName(),
                executorService,
                threadPoolExecutor,
                transportConfig.getAsyncResolverRefreshIntervalSeconds(),
                TimeUnit.SECONDS,
                5,
                updateTask
        );
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
        threadPoolExecutor.shutdown();
    }


    @Override
    public String getRegion() {
        return delegate.getRegion();
    }

    @Override
    public List<T> getClusterEndpoints() {
        if (!warmedUp) {
            updateTask.run();
            executorService.schedule(
                    backgroundTask, transportConfig.getAsyncResolverRefreshIntervalSeconds(), TimeUnit.SECONDS);
            warmedUp = true;
        }
        return resultsRef.get();
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            try {
                List<T> newList = delegate.getClusterEndpoints();
                resultsRef.getAndSet(newList);
            } catch (Exception e) {
                logger.warn("Failed to retrieve cluster endpoints from the delegate", e);
            }
        }
    };
}

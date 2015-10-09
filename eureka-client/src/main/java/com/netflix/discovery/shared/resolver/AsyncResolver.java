package com.netflix.discovery.shared.resolver;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.TimedSupervisorTask;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An async resolver that keeps a cached version of the endpoint list value for gets, and updates this cache
 * periodically in a different thread.
 *
 * @author David Liu
 */
public class AsyncResolver<T extends EurekaEndpoint> implements ClusterResolver<T> {

    private volatile boolean warmedUp = false;

    private final ClusterResolver<T> delegate;
    private final ScheduledExecutorService executorService;
    private final TimedSupervisorTask backgroundTask;
    private final AtomicReference<List<T>> resultsRef;

    public AsyncResolver(EurekaClientConfig clientConfig, ClusterResolver<T> delegate, ScheduledExecutorService executorService, ThreadPoolExecutor threadPoolExecutor) {
        this.delegate = delegate;
        this.executorService = executorService;
        this.resultsRef = new AtomicReference<>(Collections.emptyList());
        this.backgroundTask = new TimedSupervisorTask(
                this.getClass().getSimpleName(),
                executorService,
                threadPoolExecutor,
                clientConfig.getEurekaServiceUrlPollIntervalSeconds(),  // TODO use a different, dedicated config val?
                TimeUnit.SECONDS,
                5,
                updateTask
        );
    }

    @Override
    public String getRegion() {
        return delegate.getRegion();
    }

    @Override
    public List<T> getClusterEndpoints() {
        if (!warmedUp) {
            updateTask.run();
            executorService.schedule(backgroundTask, 0, TimeUnit.SECONDS);
            warmedUp = true;
        }
        return resultsRef.get();
    }

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            List<T> newList = delegate.getClusterEndpoints();
            resultsRef.getAndSet(newList);
        }
    };
}

/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.registry.eviction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.metric.EvictionQueueMetrics;
import com.netflix.eureka2.registry.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EvictionQueueImpl implements EvictionQueue {

    private static final Logger logger = LoggerFactory.getLogger(EvictionQueueImpl.class);

    private final long evictionTimeoutMs;
    private final EvictionQueueMetrics evictionQueueMetrics;

    private final Worker worker;

    private final AtomicInteger queueSize;
    private final Deque<EvictionItem> queue = new ConcurrentLinkedDeque<>();
    private final AtomicReference<Subscriber<EvictionItem>> evictionSubscriber = new AtomicReference<>();

    private final AtomicLong evictionQuota = new AtomicLong();
    private final Action0 pushAction = new Action0() {
        @Override
        public void call() {
            long now = worker.now();
            while (evictionQuota.get() > 0 && !queue.isEmpty() && queue.peek().getExpiryTime() <= now) {
                EvictionItem item = queue.poll();
                queueSize.decrementAndGet();
                evictionQuota.decrementAndGet();

                evictionQueueMetrics.decrementEvictionQueueCounter();

                logger.debug("Attempting to evict registry entry {}/{}", item.getSource(), item.getInstanceInfo().getId());
                evictionSubscriber.get().onNext(item);
            }
            long scheduleDelay = evictionTimeoutMs;
            if (!queue.isEmpty()) {
                scheduleDelay = queue.peek().getExpiryTime() - now;
                if (scheduleDelay <= 0) {
                    // We have no quota to consume expired items from the queue.
                    // To avoid rescheduling conditionally from multiple places, which would require
                    // locking, we actively reschedule the task, with reasonable frequency.
                    scheduleDelay = Math.max(100, evictionTimeoutMs / 10);
                }
            }
            worker.schedule(pushAction, scheduleDelay, TimeUnit.MILLISECONDS);
        }
    };

    @Inject
    public EvictionQueueImpl(EurekaRegistryConfig config, EurekaRegistryMetricFactory metricFactory) {
        this(config.getEvictionTimeoutMs(), metricFactory, Schedulers.computation());
    }

    public EvictionQueueImpl(long evictionTimeoutMs, EurekaRegistryMetricFactory metricFactory) {
        this(evictionTimeoutMs, metricFactory, Schedulers.computation());
    }

    public EvictionQueueImpl(long evictionTimeoutMs, EurekaRegistryMetricFactory metricFactory, Scheduler scheduler) {
        this.evictionTimeoutMs = evictionTimeoutMs;
        this.evictionQueueMetrics = metricFactory.getEvictionQueueMetrics();
        this.worker = scheduler.createWorker();

        this.queueSize = new AtomicInteger(0);

        evictionQueueMetrics.setEvictionQueueSizeMonitor(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return size();
            }
        });
    }

    @Override
    public void add(InstanceInfo instanceInfo, Source source) {
        evictionQueueMetrics.incrementEvictionQueueAddCounter();
        queue.addLast(new EvictionItem(instanceInfo, source, worker.now() + evictionTimeoutMs));
        queueSize.incrementAndGet();
    }

    @Override
    public Observable<EvictionItem> pendingEvictions() {
        return Observable.create(new OnSubscribe<EvictionItem>() {
            @Override
            public void call(Subscriber<? super EvictionItem> subscriber) {
                if (!evictionSubscriber.compareAndSet(null, (Subscriber<EvictionItem>) subscriber)) {
                    throw new IllegalStateException("Only one subscriber allowed in the eviction queue");
                }
                subscriber.setProducer(new Producer() {
                    @Override
                    public void request(long n) {
                        evictionQuota.getAndAdd(n);
                    }
                });
                worker.schedule(pushAction, evictionTimeoutMs, TimeUnit.MILLISECONDS);
            }
        });
    }

    // don't use queue.size() as .size() is not constant time for ConcurrentLinkedQueue
    @Override
    public int size() {
        int size = queueSize.get();
        if (size < 0) {
            logger.warn("Eviction queue size is less than 0: {}", size);
            size = 0;
        }
        return size;
    }

    @Override
    public void shutdown() {
        worker.unsubscribe();
        queue.clear();
    }
}

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

package com.netflix.eureka2.server.registry.eviction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.metric.EvictionQueueMetrics;
import com.netflix.eureka2.server.metric.WriteServerMetricFactory;
import com.netflix.eureka2.server.registry.Source;
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

    private final Deque<EvictionItem> queue = new ConcurrentLinkedDeque<>();
    private final AtomicReference<Subscriber<EvictionItem>> evictionSubscriber = new AtomicReference<>();

    private final AtomicLong evictionQuota = new AtomicLong();
    private final Action0 pushAction = new Action0() {
        @Override
        public void call() {
            long now = worker.now();
            while (evictionQuota.get() > 0 && !queue.isEmpty() && queue.peek().getExpiryTime() <= now) {
                EvictionItem item = queue.poll();
                evictionQuota.decrementAndGet();

                evictionQueueMetrics.decrementEvictionQueueCounter();

                logger.info("Evicting registry entry {}/{}", item.getSource(), item.getInstanceInfo().getId());
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
    public EvictionQueueImpl(WriteServerConfig config, WriteServerMetricFactory metricFactory) {
        this(config, metricFactory, Schedulers.computation());
    }

    public EvictionQueueImpl(WriteServerConfig config, WriteServerMetricFactory metricFactory, Scheduler scheduler) {
        this.evictionTimeoutMs = config.getEvictionTimeoutMs();
        this.evictionQueueMetrics = metricFactory.getEvictionQueueMetrics();
        this.worker = scheduler.createWorker();

        evictionQueueMetrics.setEvictionQueueSizeMonitor(this);
    }

    @Override
    public void add(InstanceInfo instanceInfo, Source source) {
        evictionQueueMetrics.incrementEvictionQueueAddCounter();
        queue.addLast(new EvictionItem(instanceInfo, source, worker.now() + evictionTimeoutMs));
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

    @Override
    public int size() {
        return queue.size();
    }
}

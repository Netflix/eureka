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

package com.netflix.rx.eureka.server.registry;

import com.netflix.rx.eureka.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Tomasz Bak
 */
public class EvictionQueueImpl implements EvictionQueue {

    private static final Logger logger = LoggerFactory.getLogger(EvictionQueueImpl.class);

    private final Worker worker;

    private final long evictionTimeoutMs;

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

                logger.info("Evicting registry entry {}/{}", item.getSource(), item.getInstanceInfo().getId());
                evictionSubscriber.get().onNext(item);
            }
            long scheduleDelay = evictionTimeoutMs;
            if (!queue.isEmpty()) {
                scheduleDelay = queue.peek().getExpiryTime() - now;
            }
            worker.schedule(pushAction, scheduleDelay, TimeUnit.MILLISECONDS);
        }
    };

    public EvictionQueueImpl(long evictionTimeoutMs) {
        this.evictionTimeoutMs = evictionTimeoutMs;
        this.worker = Schedulers.computation().createWorker();
    }

    @Override
    public void add(InstanceInfo instanceInfo, Source source) {
        queue.addLast(new EvictionItem(instanceInfo, source, System.currentTimeMillis() + evictionTimeoutMs));
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
}

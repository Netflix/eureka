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

package com.netflix.rx.eureka.utils.rx;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Observable over an external resource that must be access in a synchronous way. Background task
 * ({@link ResourceLoader}) is scheduled periodically to refresh cached resource data.
 * The loader job is scheduled on first subscription, and if idle timeout is defined will be stopped
 * if there is no subscription over that amount of time. Subsequent subscription will reactivate it again.
 * {@link ResourceLoader} returns an instance of {@link ResourceUpdate}, which holds new data snapshot, and
 * optionally cancellation items, that are pushed to already subscribed clients. The cancellation concept
 * must be implemented by the type T and its consumer client.
 *
 * @author Tomasz Bak
 */
public class ResourceObservable<T> {

    private final ResourceLoader<T> loader;
    private final Scheduler scheduler;
    private final long refreshInterval;
    private final long idleTimeout;
    private final TimeUnit timeUnit;

    private final ReentrantLock lock = new ReentrantLock();
    private ResourceLoaderExecutor executor; // Updates guarded by lock
    private final AtomicInteger subscriptionCounter = new AtomicInteger();

    private final Observable<T> observable;

    public ResourceObservable(ResourceLoader<T> loader, Scheduler scheduler, long refreshInterval, long idleTimeout, TimeUnit timeUnit) {
        this.loader = loader;
        this.scheduler = scheduler;
        this.refreshInterval = refreshInterval;
        this.idleTimeout = idleTimeout;
        this.timeUnit = timeUnit;

        this.observable = Observable.create(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                lock.lock();
                try {
                    if (executor == null) {
                        executor = new ResourceLoaderExecutor();
                        executor.call();
                    }
                    subscriptionCounter.incrementAndGet();

                    for (T entry : executor.getDataSnapshot()) {
                        subscriber.onNext(entry);
                    }
                    executor.getDataUpdates().subscribe(subscriber);
                } finally {
                    lock.unlock();
                }
                subscriber.add(new Subscription() {
                    @Override
                    public void unsubscribe() {
                        if (subscriptionCounter.decrementAndGet() == 0) {
                            scheduleCleanupTask();
                        }
                    }

                    @Override
                    public boolean isUnsubscribed() {
                        return false;
                    }
                });
            }
        });
    }

    private void scheduleCleanupTask() {
        if (idleTimeout <= 0) {
            return;
        }

        Worker worker = scheduler.createWorker();
        try {
            worker.schedule(new Action0() {
                @Override
                public void call() {
                    lock.lock();
                    try {
                        if (subscriptionCounter.get() == 0 && executor != null) {
                            executor.cancel();
                            executor = null;
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }, idleTimeout, timeUnit);
        } finally {
            worker.unsubscribe();
        }
    }

    public Observable<T> getObservable() {
        return observable;
    }

    public static <T> Observable<T> fromResource(ResourceLoader<T> loader) {
        return new ResourceObservable<T>(loader, Schedulers.io(), -1, -1, TimeUnit.MILLISECONDS).getObservable();
    }

    public static <T> Observable<T> fromResource(ResourceLoader<T> loader, long refreshInterval, TimeUnit timeUnit) {
        return new ResourceObservable<T>(loader, Schedulers.io(), refreshInterval, -1, timeUnit).getObservable();
    }

    public static <T> Observable<T> fromResource(ResourceLoader<T> loader, long refreshInterval, long idleTimeout, TimeUnit timeUnit) {
        return new ResourceObservable<T>(loader, Schedulers.io(), refreshInterval, idleTimeout, timeUnit).getObservable();
    }

    public static <T> Observable<T> fromResource(ResourceLoader<T> loader, long refreshInterval, long idleTimeout, TimeUnit timeUnit, Scheduler scheduler) {
        return new ResourceObservable<T>(loader, scheduler, refreshInterval, idleTimeout, timeUnit).getObservable();
    }

    public static class ResourceUpdate<T> {
        private final Set<T> added;
        private final Set<T> cancelled;

        public ResourceUpdate(Set<T> added, Set<T> cancelled) {
            this.added = added;
            this.cancelled = cancelled;
        }

        public Set<T> getAdded() {
            return added;
        }

        public Set<T> getCancelled() {
            return cancelled;
        }
    }

    public interface ResourceLoader<T> {
        ResourceUpdate<T> reload(Set<T> currentSnapshot);
    }

    public static class ResourceLoaderException extends RuntimeException {

        private final boolean recoverable;

        public ResourceLoaderException(String message, boolean recoverable, Throwable cause) {
            super(message, cause);
            this.recoverable = recoverable;
        }

        public boolean isRecoverable() {
            return recoverable;
        }
    }

    class ResourceLoaderExecutor implements Action0 {

        private final Worker worker = scheduler.createWorker();
        private volatile Set<T> dataSnapshot = new HashSet<>();
        private final PublishSubject<T> dataUpdates = PublishSubject.create();
        private volatile boolean terminate;
        private Subscription rescheduleSubscription;

        public Set<T> getDataSnapshot() {
            return dataSnapshot;
        }

        public PublishSubject<T> getDataUpdates() {
            return dataUpdates;
        }

        void reschedule() {
            if (!terminate && refreshInterval > 0) {
                rescheduleSubscription = worker.schedule(this, refreshInterval, timeUnit);
            }
        }

        void cancel() {
            terminate = true;
            rescheduleSubscription.unsubscribe();
            worker.unsubscribe();
        }

        @Override
        public void call() {
            if (terminate) {
                return;
            }

            ResourceUpdate<T> update = loader.reload(dataSnapshot);

            // Push new data to existing subscribers. No new subscription is allowed when doing that.
            lock.lock();
            try {
                for (T entry : update.getCancelled()) {
                    dataUpdates.onNext(entry);
                }
                for (T entry : update.getAdded()) {
                    if (!dataSnapshot.contains(entry)) {
                        dataUpdates.onNext(entry);
                    }
                }
                dataSnapshot = update.getAdded();
            } finally {
                lock.unlock();
            }
            reschedule();
        }
    }
}

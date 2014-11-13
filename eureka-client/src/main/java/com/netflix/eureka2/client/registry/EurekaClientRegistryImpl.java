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

package com.netflix.eureka2.client.registry;

import com.netflix.eureka2.client.metric.EurekaClientRegistryMetrics;
import com.netflix.eureka2.datastore.NotificationsSubject;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.InstanceInfoInitStateHolder;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.InstanceInfo;
import rx.Observable;
import rx.functions.Func1;

import javax.inject.Inject;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static com.netflix.eureka2.interests.ChangeNotification.Kind;

/**
 * @author Tomasz Bak
 */
public class EurekaClientRegistryImpl implements EurekaClientRegistry<InstanceInfo> {

    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final EurekaClientRegistryMetrics metrics;

    private final ConcurrentHashMap<String, InstanceInfo> internalStore;

    @Inject
    public EurekaClientRegistryImpl(EurekaClientRegistryMetrics metrics) {
        this.metrics = metrics;
        this.metrics.setRegistrySizeMonitor(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return internalStore.size();
            }
        });

        internalStore = new ConcurrentHashMap<>();
        indexRegistry = new IndexRegistryImpl<>();
        notificationSubject = NotificationsSubject.create();
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        internalStore.put(instanceInfo.getId(), instanceInfo);
        notificationSubject.onNext(new ChangeNotification<>(Kind.Add, instanceInfo));
        return Observable.empty();
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        InstanceInfo removed = internalStore.remove(instanceInfo.getId());
        if (removed != null) {
            notificationSubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Delete, instanceInfo));
        }
        return Observable.empty();
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        InstanceInfo previous = internalStore.put(updatedInfo.getId(), updatedInfo);
        if (previous == null) {
            notificationSubject.onNext(new ChangeNotification<>(Kind.Add, updatedInfo));
        } else {
            notificationSubject.onNext(new ModifyNotification<InstanceInfo>(updatedInfo, deltas));
        }
        return Observable.empty();
    }

    /**
     * Return a snapshot of the current registry for the passed {@code interest} as a stream of {@link InstanceInfo}s.
     * This view of the snapshot is eventual consistent and any instances that successfully registers while the
     * stream is being processed might be added to the stream. Note that this stream terminates as opposed to
     * forInterest.
     *
     * @return A stream of {@link InstanceInfo}s for the passed {@code interest}. The stream represent a snapshot
     * of the registry for the interest.
     */
    @Override
    public Observable<InstanceInfo> forSnapshot(final Interest<InstanceInfo> interest) {
        return Observable.from(internalStore.values())
                .filter(new Func1<InstanceInfo, Boolean>() {
                    @Override
                    public Boolean call(InstanceInfo instanceInfo) {
                        return instanceInfo != null && interest.matches(instanceInfo);
                    }
                });
    }

    /**
     * Return an observable of all matching InstanceInfo for the current registry snapshot,
     * as {@link ChangeNotification}s
     * @param interest
     * @return an observable of all matching InstanceInfo for the current registry snapshot,
     * as {@link ChangeNotification}s
     */
    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        try {
            // TODO: this method can be run concurrently from different channels, unless we run everything on single server event loop.
            notificationSubject.pause(); // Pause notifications till we get a snapshot of current registry (registry.values())
            if (interest instanceof MultipleInterests) {
                return indexRegistry.forCompositeInterest((MultipleInterests) interest, this);
            } else {
                return indexRegistry.forInterest(interest, notificationSubject,
                        new InstanceInfoInitStateHolder(getSnapshotForInterest(interest)));
            }
        } finally {
            notificationSubject.resume();
        }
    }

    private Iterator<ChangeNotification<InstanceInfo>> getSnapshotForInterest(final Interest<InstanceInfo> interest) {
        return new FilteredIterator(interest, internalStore.values().iterator());
    }

    @Override
    public Observable<Void> shutdown() {
        return indexRegistry.shutdown();
    }

    private static class FilteredIterator implements Iterator<ChangeNotification<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;
        private final Iterator<InstanceInfo> delegate;
        private ChangeNotification<InstanceInfo> next;

        private FilteredIterator(Interest<InstanceInfo> interest, Iterator<InstanceInfo> delegate) {
            this.interest = interest;
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            if (null != next) {
                return true;
            }

            while (delegate.hasNext()) { // Iterate till we get a matching item.
                InstanceInfo possibleNext = delegate.next();
                ChangeNotification<InstanceInfo> notification = new ChangeNotification<>(Kind.Add, possibleNext);
                if (notification != null && interest.matches(notification.getData())) {
                    next = notification;
                    return true;
                }
            }
            return false;
        }

        @Override
        public ChangeNotification<InstanceInfo> next() {
            if (hasNext()) {
                ChangeNotification<InstanceInfo> next = this.next;
                this.next = null; // Forces hasNext() to peek the next item.
                return next;
            }
            throw new NoSuchElementException("No more notifications.");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported for this iterator.");
        }
    }
}

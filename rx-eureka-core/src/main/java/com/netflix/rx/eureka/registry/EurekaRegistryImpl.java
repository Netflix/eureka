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

package com.netflix.rx.eureka.registry;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.rx.eureka.datastore.NotificationsSubject;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.IndexRegistry;
import com.netflix.rx.eureka.interests.IndexRegistryImpl;
import com.netflix.rx.eureka.interests.InstanceInfoInitStateHolder;
import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.interests.ModifyNotification;
import com.netflix.rx.eureka.interests.MultipleInterests;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * TODO: fix race in adding/removing from store and sending notification to notificationSubject
 * TODO: how expensive is Observable.empty()?
 * TODO: registry lifecycle management APIs
 * TODO: threadpool for async add/put to internalStore?
 * @author David Liu
 */
public class EurekaRegistryImpl implements EurekaRegistry<InstanceInfo> {

    /**
     * TODO: define a better contract for base implementation and decorators
     */
    protected final ConcurrentHashMap<String, Lease<InstanceInfo>> internalStore;
    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final EurekaRegistryMetrics metrics;

    public EurekaRegistryImpl(EurekaRegistryMetrics metrics) {
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

    // -------------------------------------------------
    // Registry manipulation
    // -------------------------------------------------

    /**
     * get the lease for the instance specified
     * @param instanceId Instance Id for which the lease is to be returned.
     *
     * @return Lease for the passed instance Id.
     *
     * @deprecated Not used anywhere
     */
    @Deprecated
    public Observable<Lease<InstanceInfo>> getLease(final String instanceId) {
        return Observable.just(internalStore.get(instanceId));
    }

    /**
     * @deprecated Not used anywhere
     */
    @Deprecated
    public boolean contains(final String instanceId) {
        return internalStore.contains(instanceId);
    }

    /**
     * Can we remove lease duration?
     */
    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return register(instanceInfo, Origin.LOCAL);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo, Origin origin) {
        Lease<InstanceInfo> lease = new Lease<>(instanceInfo, origin);
        internalStore.put(instanceInfo.getId(), lease);
        metrics.incrementRegistrationCounter(origin);
        notificationSubject.onNext(lease.getHolderSnapshot());

        return Observable.empty();
    }

    //TODO: implement channel level ownership of registered instanceInfo and only process unregister if
    //request is from the current owner.
    @Override
    public Observable<Void> unregister(final String instanceId) {
        final Lease<InstanceInfo> remove = internalStore.remove(instanceId);
        if (remove != null) {
            metrics.incrementUnregistrationCounter(remove.getOrigin());
            notificationSubject.onNext(new ChangeNotification<>(ChangeNotification.Kind.Delete,
                    remove.getHolder()));
        }
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                if (remove == null) {
                    subscriber.onError(new InstanceNotRegisteredException(instanceId));
                } else {
                    subscriber.onCompleted();
                }
            }
        });
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        final String instanceId = updatedInfo.getId();

        metrics.incrementUpdateCounter();

        Lease<InstanceInfo> newLease = new Lease<>(updatedInfo);
        Lease<InstanceInfo> existing = internalStore.put(instanceId, newLease);

        if (existing == null) {  // is an add
            notificationSubject.onNext(newLease.getHolderSnapshot());
        } else {  // is a modify
            notificationSubject.onNext(new ModifyNotification<>(updatedInfo, deltas));
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
                .map(new Func1<Lease<InstanceInfo>, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(Lease<InstanceInfo> lease) {
                        return lease.getHolder();
                    }
                })
                .filter(new Func1<InstanceInfo, Boolean>() {
                    @Override
                    public Boolean call(InstanceInfo instanceInfo) {
                        return interest.matches(instanceInfo);
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

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, final Origin origin) {
        return forInterest(interest).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                Lease<InstanceInfo> lease = internalStore.get(changeNotification.getData().getId());
                return lease != null && lease.getOrigin() == origin;
            }
        });
    }

    @Override
    public Observable<Void> shutdown() {
        return indexRegistry.shutdown();
    }

    private Iterator<ChangeNotification<InstanceInfo>> getSnapshotForInterest(final Interest<InstanceInfo> interest) {
        final Collection<Lease<InstanceInfo>> leases = internalStore.values();
        return new FilteredIterator(interest, leases.iterator());
    }

    private static class FilteredIterator implements Iterator<ChangeNotification<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;
        private final Iterator<Lease<InstanceInfo>> delegate;
        private ChangeNotification<InstanceInfo> next;

        private FilteredIterator(Interest<InstanceInfo> interest, Iterator<Lease<InstanceInfo>> delegate) {
            this.interest = interest;
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            if (null != next) {
                return true;
            }

            while (delegate.hasNext()) { // Iterate till we get a matching item.
                Lease<InstanceInfo> possibleNext = delegate.next();
                if (interest.matches(possibleNext.getHolder())) {
                    next = possibleNext.getHolderSnapshot();
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

    // pretty print for debugging
    @Override
    public String toString() {
        return prettyString();
    }

    private String prettyString() {
        StringBuilder sb = new StringBuilder("EurekaRegistryImpl\n");
        for (Map.Entry<String, Lease<InstanceInfo>> entry : internalStore.entrySet()) {
            sb.append(entry).append("\n");
        }
        sb.append(indexRegistry.toString());

        return sb.toString();
    }
}

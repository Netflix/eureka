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

package com.netflix.eureka.registry;

import com.netflix.eureka.datastore.NotificationsSubject;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.IndexRegistry;
import com.netflix.eureka.interests.InstanceInfoInitStateHolder;
import com.netflix.eureka.interests.Interest;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: fix race in adding/removing from store and sending notification to notificationSubject
 * TODO: how expensive is Observable.empty()?
 * TODO: registry lifecycle management APIs
 * TODO: threadpool for async add/put to internalStore?
 * @author David Liu
 */
public class LeasedInstanceRegistry implements EurekaRegistry {

    protected final ConcurrentHashMap<String, Lease<InstanceInfo>> internalStore;
    private final RegistrySnapshot internalSnapshot;
    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;

    public LeasedInstanceRegistry() {
        internalStore = new ConcurrentHashMap<String, Lease<InstanceInfo>>();
        internalSnapshot = new RegistrySnapshot();
        indexRegistry = new IndexRegistry<InstanceInfo>();
        notificationSubject = NotificationsSubject.create();
    }

    // -------------------------------------------------
    // Registry metadata
    // -------------------------------------------------
    public String getRegion() {
        return null;
    }

    public int getSize() {
        return internalStore.size();
    }

    // -------------------------------------------------
    // Registry manipulation
    // -------------------------------------------------

    /**
     * Renewal the lease for instance with the given id
     * @param instanceId
     * @return true if successfully renewed
     */
    public Observable<Void> renewLease(final String instanceId) {
        return renewLease(instanceId, Lease.DEFAULT_LEASE_DURATION_MILLIS);
    }

    /**
     * Renewal the lease for instance with the given id for the given duration
     * @param instanceId
     * @param durationMillis
     * @return true if successfully renewed
     */
    public Observable<Void> renewLease(final String instanceId, final long durationMillis) {
        Lease<?> lease = internalStore.get(instanceId);
        if (lease != null) {
            lease.renew(durationMillis);
        }

        return Observable.empty();
    }

    /**
     * Cancel the lease for the instance with the given id
     * @param instanceId
     * @return true of successfully canceled
     */
    public Observable<Void> cancelLease(final String instanceId) {
        return unregister(instanceId);
    }

    /**
     * check to see if the lease of the specified instance has expired.
     * @param instanceId the instanceId of the instance to check
     * @return true if instance exist and has expired, false if exist and not expired
     */
    public Observable<Boolean> hasExpired(final String instanceId) {
        Lease<?> lease = internalStore.get(instanceId);

        if (lease != null) {
            return Observable.just(lease.hasExpired());
        } else {
            return Observable.error(new InstanceNotRegisteredException(instanceId));
        }
    }

    /**
     * get the lease for the instance specified
     * @param instanceId
     * @return
     */
    public Observable<Lease<InstanceInfo>> getLease(final String instanceId) {
        return Observable.just(internalStore.get(instanceId));
    }

    public Observable<InstanceInfo> getInstanceInfo(final String instanceId) {
        return Observable.create(new Observable.OnSubscribe<InstanceInfo>() {
            @Override
            public void call(Subscriber<? super InstanceInfo> subscriber) {
                Lease<InstanceInfo> lease = internalStore.get(instanceId);
                if (lease != null) {
                    subscriber.onNext(lease.getHolder());
                } else {
                    subscriber.onError(new InstanceNotRegisteredException(instanceId));
                }
                subscriber.onCompleted();
            }
        });
    }

    public boolean contains(final String instanceId) {
        return internalStore.contains(instanceId);
    }


    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return register(instanceInfo, Lease.DEFAULT_LEASE_DURATION_MILLIS);
    }

    public Observable<Void> register(final InstanceInfo instanceInfo, final long durationMillis) {
        Lease<InstanceInfo> lease = new Lease<InstanceInfo>(
                instanceInfo,
                durationMillis);
        internalStore.put(instanceInfo.getId(), lease);
        notificationSubject.onNext(lease.getHolderSnapshot());

        return Observable.empty();
    }

    public Observable<Void> unregister(final String instanceId) {
        final Lease<InstanceInfo> remove = internalStore.remove(instanceId);
        if (remove != null) {
            notificationSubject.onNext(new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete,
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
    public Observable<Void> update(InstanceInfo instanceInfo) {
        final String instanceId = instanceInfo.getId();

        Lease<InstanceInfo> newLease = new Lease<InstanceInfo>(instanceInfo);
        Lease<InstanceInfo> existing = internalStore.put(instanceId, newLease);

        if (existing == null) {  // is an add
            notificationSubject.onNext(newLease.getHolderSnapshot());
        } else {  // is a modify
            notificationSubject.onNext(new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Modify,
                    instanceInfo));
        }

        return Observable.empty();
    }

    /**
     * Update the status of the instance with the given id
     * @param instanceId
     * @param status
     * @return true of successfully updated
     */
    public Observable<Void> updateStatus(String instanceId, InstanceInfo.Status status) {
        Lease<InstanceInfo> lease = internalStore.get(instanceId);

        if (lease == null) {
            return Observable.error(new InstanceNotRegisteredException(instanceId));
        }

        InstanceInfo current = lease.getHolder();
        InstanceInfo update = new InstanceInfo.Builder()
                .withInstanceInfo(current)
                .withStatus(status)
                .build();

        return update(update);
    }

    /**
     * Return an observable of all matching InstanceInfo for the current registry snapshot
     * @param key
     * @param value
     * @param <T>
     * @return
     */
    public <T> Observable<InstanceInfo> allMatching(final Index key, final T value) {
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
                        return instanceInfo.match(key, value);
                    }
                });
    }

    /**
     * Return an observable of all matching InstanceInfo for the current registry snapshot,
     * as {@link ChangeNotification}s
     * @param interest
     * @return
     */
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        try {
            notificationSubject.pause(); // Pause notifications till we get a snapshot of current registry (registry.values())
            return indexRegistry.forInterest(interest, notificationSubject,
                    new InstanceInfoInitStateHolder(internalSnapshot));
        } finally {
            internalStore.values();
            notificationSubject.resume();
        }
    }

    /**
     * Delegate collection to return iterables of ChangeNotification<InstanceInfo> of the internalStore values
     */
    private class RegistrySnapshot extends AbstractCollection<ChangeNotification<InstanceInfo>> {

        @Override
        public Iterator<ChangeNotification<InstanceInfo>> iterator() {
            final Iterator<Lease<InstanceInfo>> internalStoreIterator = internalStore.values().iterator();

            return new Iterator<ChangeNotification<InstanceInfo>>() {
                @Override
                public boolean hasNext() {
                    return internalStoreIterator.hasNext();
                }

                @Override
                public ChangeNotification<InstanceInfo> next() {
                    return internalStoreIterator.next().getHolderSnapshot();
                }

                @Override
                public void remove() {
                    internalStoreIterator.remove();
                }
            };
        }

        @Override
        public int size() {
            return internalStore.size();
        }
    }
}

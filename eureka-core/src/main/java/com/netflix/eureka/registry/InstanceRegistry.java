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

import com.netflix.eureka.datastore.Store;
import com.netflix.eureka.interests.Interest;
import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: registry lifecycle management APIs
 * TODO: threadpool for async add/put to internalStore?
 * @author David Liu
 */
public class InstanceRegistry extends Store<InstanceInfo> {

    private final ConcurrentHashMap<String, Lease<InstanceInfo>> internalStore;

    public InstanceRegistry() {
        internalStore = new ConcurrentHashMap<String, Lease<InstanceInfo>>();
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
        Lease<InstanceInfo> lease = internalStore.get(instanceId);
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
        Lease<InstanceInfo> lease = internalStore.remove(instanceId);
        if (lease != null) {
            lease.cancel();
        }

        return Observable.empty();
    }

    /**
     * check to see if the lease of the specified instance has expired.
     * @param instanceId the instanceId of the instance to check
     * @return true if instance exist and has expired, false if exist and not expired
     */
    public Observable<Boolean> hasExpired(final String instanceId) {
        Lease<InstanceInfo> lease = internalStore.get(instanceId);

        if (lease != null) {
            return Observable.just(lease.hasExpired());
        } else {
            return Observable.error(EurekaRegistryException.instanceNotFound(instanceId));
        }
    }

    /**
     * get the lease for the instance specified
     * @param instanceId
     * @return
     */
    public Observable<Lease<InstanceInfo>> getLease(String instanceId) {
        return Observable.just(internalStore.get(instanceId));
    }

    public Observable<Boolean> register(final InstanceInfo instanceInfo) {
        return register(instanceInfo, Lease.DEFAULT_LEASE_DURATION_MILLIS);
    }

    public Observable<Boolean> register(final InstanceInfo instanceInfo, final long durationMillis) {
        Lease<InstanceInfo> lease = new Lease<InstanceInfo>(instanceInfo, durationMillis);
        internalStore.put(instanceInfo.getId(), lease);

        return Observable.just(true);
    }

    public Observable<Void> unregister(String instanceId) {
        return cancelLease(instanceId);
    }
    /**
     * Update the status of the instance with the given id
     * @param instanceId
     * @param status
     * @return true of successfully updated
     */
    public Observable<Boolean> updateStatus(String instanceId, InstanceInfo.Status status) {
        Lease<InstanceInfo> lease = internalStore.get(instanceId);

        if (lease == null) {
            return Observable.error(EurekaRegistryException.instanceNotFound(instanceId));
        }

        InstanceInfo current = lease.getHolder();
        InstanceInfo update = new InstanceInfo.Builder()
                .withInstanceInfo(current)
                .withStatus(status)
                .build();

        return Observable.just(lease.compareAndSet(current, update));
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
                        if (lease.getHolder().match(key, value)) {
                            return lease.getHolder();
                        }
                        return null;
                    }
                })
                .filter(new Func1<InstanceInfo, Boolean>() {
                    @Override
                    public Boolean call(InstanceInfo instanceInfo) {
                        return instanceInfo != null;
                    }
                });
    }

    /**
     * Return an observable of all matching InstanceInfo for the current registry snapshot as ADD,
     * then emit all subsequent MODIFY and DELETE for the matching InstanceInfos
     * @param interest
     * @return
     */
    public Observable<InstanceInfo> forInterest(Interest interest) {
        return Observable.empty();
    }


    // -------------------------------------------------
    // Store methods
    // -------------------------------------------------

    @Override
    public Observable<Boolean> contains(String id) {
        return Observable.just(internalStore.containsKey(id));
    }

    @Override
    public Observable<InstanceInfo> get(String id) {
        return Observable.just(internalStore.get(id).getHolder());
    }

    @Override
    protected Observable<Boolean> add(final InstanceInfo item) {
        Lease<InstanceInfo> previous = internalStore.putIfAbsent(item.getId(), new Lease<InstanceInfo>(item));
        return Observable.just(previous == null);
    }

    @Override
    protected Observable<Void> set(final InstanceInfo item) {
        internalStore.put(item.getId(), new Lease<InstanceInfo>(item));
        return Observable.empty();
    }

    @Override
    protected Observable<InstanceInfo> remove(String id) {
        return Observable.empty();
    }
}

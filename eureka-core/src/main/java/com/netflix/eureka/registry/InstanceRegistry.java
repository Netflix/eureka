package com.netflix.eureka.registry;

import com.netflix.eureka.datastore.Store;
import com.netflix.eureka.interests.Interest;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: registry lifecycle management APIs
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
        return 0;
    }

    // -------------------------------------------------
    // Registry manipulation
    // -------------------------------------------------

    /**
     * Renewal the lease for instance with the given id
     * @param instanceId
     * @return true if successfully renewed
     */
    public Observable<Boolean> renewLease(String instanceId) {
        return Observable.empty();
    }

    /**
     * Renewal the lease for instance with the given id for the given duration
     * @param instanceId
     * @param durationMillis
     * @return true if successfully renewed
     */
    public Observable<Boolean> renewLease(String instanceId, long durationMillis) {
        return Observable.empty();
    }

    /**
     * Cancel the lease for the instance with the given id
     * @param instanceId
     * @return true of successfully canceled
     */
    public Observable<Boolean> cancelLease(String instanceId) {
        return Observable.empty();
    }

    /**
     * check to see if the lease of the specified instance has expired.
     * @param instanceId the instanceId of the instance to check
     * @return true if instance exist and has expired, false if exist and not expired
     */
    public Observable<Boolean> hasExpired(String instanceId) {
        return Observable.empty();
    }

    /**
     * get the lease for the instance specified
     * @param instanceId
     * @return
     */
    public Observable<Lease<InstanceInfo>> getLease(String instanceId) {
        return Observable.empty();
    }

    public Observable<Boolean> register(InstanceInfo instanceInfo, long durationMillis) {
        return Observable.empty();
    }

    public Observable<Boolean> unregister(String instanceId) {
        return Observable.empty();
    }
    /**
     * Update the status of the instance with the given id
     * @param instanceId
     * @param status
     * @return true of successfully updated
     */
    public Observable<Boolean> updateStatus(String instanceId, InstanceInfo.Status status) {
        return Observable.empty();
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
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                internalStore.put(item.getId(), new Lease<InstanceInfo>(item));
            }
        });
    }

    @Override
    protected Observable<Void> set(final InstanceInfo item) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                internalStore.put(item.getId(), new Lease<InstanceInfo>(item));
            }
        });
    }

    @Override
    protected Observable<InstanceInfo> remove(String id) {
        return Observable.empty();
    }
}

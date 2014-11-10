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

import javax.inject.Inject;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.rx.eureka.data.MultiSourcedDataHolder;
import com.netflix.rx.eureka.data.NotifyingInstanceInfoHolder;
import com.netflix.rx.eureka.data.Source;
import com.netflix.rx.eureka.datastore.NotificationsSubject;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.IndexRegistry;
import com.netflix.rx.eureka.interests.IndexRegistryImpl;
import com.netflix.rx.eureka.interests.InstanceInfoInitStateHolder;
import com.netflix.rx.eureka.interests.Interest;
import com.netflix.rx.eureka.interests.MultipleInterests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

/**
 * TODO: fix race in adding/removing from store and sending notification to notificationSubject
 * TODO: threadpool for async add/put to internalStore?
 * @author David Liu
 */
public class EurekaRegistryImpl implements EurekaRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaRegistryImpl.class);

    /**
     * TODO: define a better contract for base implementation and decorators
     */
    protected final ConcurrentHashMap<String, MultiSourcedDataHolder<InstanceInfo>> internalStore;
    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final EurekaRegistryMetrics metrics;

    @Inject
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
     * Can we remove lease duration?
     */
    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return register(instanceInfo, Source.localSource());
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo, Source source) {
        MultiSourcedDataHolder<InstanceInfo> newHolder = new NotifyingInstanceInfoHolder(notificationSubject, instanceInfo.getId());
        MultiSourcedDataHolder<InstanceInfo> currentHolder = internalStore.putIfAbsent(instanceInfo.getId(), newHolder);

        Observable<Void> toReturn;
        if (currentHolder != null) {
            toReturn = currentHolder.update(source, instanceInfo);
            metrics.incrementRegistrationCounter(source.getOrigin());  // this is a true new registration
        } else {
            toReturn = newHolder.update(source, instanceInfo);
        }

        toReturn.toBlocking().firstOrDefault(null);  // TODO: revisit whether we should plumb the observable all the way or stop here
        return Observable.empty();
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        return unregister(instanceId, Source.localSource());
    }

    @Override
    public Observable<Void> unregister(final String instanceId, Source source) {
        final MultiSourcedDataHolder<InstanceInfo> currentHolder = internalStore.get(instanceId);

        Observable<Void> toReturn = Observable.empty();
        if (currentHolder != null) {
            toReturn = currentHolder.remove(source);
            metrics.incrementUnregistrationCounter(source.getOrigin());  // FIXME: move this to the expiry queue?
        }

        toReturn.toBlocking().firstOrDefault(null);
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                if (currentHolder == null) {
                    subscriber.onError(new InstanceNotRegisteredException(instanceId));
                } else {
                    subscriber.onCompleted();
                }
            }
        });
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        return update(updatedInfo, deltas, Source.localSource());
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas, Source source) {
        MultiSourcedDataHolder<InstanceInfo> newHolder = new NotifyingInstanceInfoHolder(notificationSubject, updatedInfo.getId());
        MultiSourcedDataHolder<InstanceInfo> currentHolder = internalStore.putIfAbsent(updatedInfo.getId(), newHolder);

        Observable<Void> toReturn;
        if (currentHolder != null) {  // this is an add
            toReturn = currentHolder.update(source, updatedInfo);
        } else {
            toReturn = newHolder.update(source, updatedInfo);
        }

        metrics.incrementUpdateCounter();

        toReturn.toBlocking().firstOrDefault(null);
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
                .map(new Func1<MultiSourcedDataHolder<InstanceInfo>, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        ChangeNotification<InstanceInfo> notification = holder.getChangeNotification();
                        return notification == null ? null : notification.getData();
                    }
                })
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

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, final Source source) {
        return forInterest(interest).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                MultiSourcedDataHolder<InstanceInfo> holder = internalStore.get(changeNotification.getData().getId());
                return holder != null && source.equals(holder.getSource());
            }
        });
    }

    @Override
    public Observable<Void> shutdown() {
        return indexRegistry.shutdown();
    }

    private Iterator<ChangeNotification<InstanceInfo>> getSnapshotForInterest(final Interest<InstanceInfo> interest) {
        final Collection<MultiSourcedDataHolder<InstanceInfo>> eurekaHolders = internalStore.values();
        return new FilteredIterator(interest, eurekaHolders.iterator());
    }

    private static class FilteredIterator implements Iterator<ChangeNotification<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;
        private final Iterator<MultiSourcedDataHolder<InstanceInfo>> delegate;
        private ChangeNotification<InstanceInfo> next;

        private FilteredIterator(Interest<InstanceInfo> interest, Iterator<MultiSourcedDataHolder<InstanceInfo>> delegate) {
            this.interest = interest;
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            if (null != next) {
                return true;
            }

            while (delegate.hasNext()) { // Iterate till we get a matching item.
                MultiSourcedDataHolder<InstanceInfo> possibleNext = delegate.next();
                ChangeNotification<InstanceInfo> notification = possibleNext.getChangeNotification();
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

    // pretty print for debugging
    @Override
    public String toString() {
        return prettyString();
    }

    private String prettyString() {
        StringBuilder sb = new StringBuilder("EurekaRegistryImpl\n");
        for (Map.Entry<String, MultiSourcedDataHolder<InstanceInfo>> entry : internalStore.entrySet()) {
            sb.append(entry).append("\n");
        }
        sb.append(indexRegistry.toString());

        return sb.toString();
    }
}

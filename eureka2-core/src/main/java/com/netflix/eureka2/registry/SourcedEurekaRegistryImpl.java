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

package com.netflix.eureka2.registry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.InstanceInfoInitStateHolder;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.registry.RegistrationFunctions.InstanceInfoWithSource;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.PauseableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import static com.netflix.eureka2.registry.RegistrationFunctions.toSourcedChangeNotificationStream;

/**
 * An implementation of {@link com.netflix.eureka2.registry.SourcedEurekaRegistry} that uses a
 * {@link com.netflix.eureka2.registry.MultiSourcedDataHolder} to store multiple copies of entries when the copies
 * are from independent sources. Note that a true register/update/unregister result is only returned when the
 * entries holder is added/removed.
 *
 * @author David Liu
 */
@Singleton
public class SourcedEurekaRegistryImpl implements SourcedEurekaRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(SourcedEurekaRegistryImpl.class);

    protected final ConcurrentMap<String, NotifyingInstanceInfoHolder> internalStore;
    private final PauseableSubject<ChangeNotification<InstanceInfo>> pauseableSubject;  // subject for all changes in the registry
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final EurekaRegistryMetrics metrics;

    private final Subject<Observable<ChangeNotification<InstanceInfoWithSource>>, Observable<ChangeNotification<InstanceInfoWithSource>>>
            registrationSubject = new SerializedSubject<>(PublishSubject.<Observable<ChangeNotification<InstanceInfoWithSource>>>create());

    /**
     * All registrations are serialized, and each registration subscription updates this data structure. If there is
     * another subscription with the given {origin, source, id}, it is overwritten, so no longer taken into consideration.
     */
    private final ConcurrentMap<String, Source> approvedRegistrations = new ConcurrentHashMap<>();

    public SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory metricsFactory) {
        this(new IndexRegistryImpl<InstanceInfo>(), metricsFactory, Schedulers.computation());
    }

    public SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory metricsFactory, Scheduler scheduler) {
        this(new IndexRegistryImpl<InstanceInfo>(), metricsFactory, scheduler);
    }

    @Inject
    public SourcedEurekaRegistryImpl(IndexRegistry indexRegistry, EurekaRegistryMetricFactory metricsFactory) {
        this(indexRegistry, metricsFactory, Schedulers.computation());
    }

    public SourcedEurekaRegistryImpl(IndexRegistry<InstanceInfo> indexRegistry, EurekaRegistryMetricFactory metricsFactory, Scheduler scheduler) {
        this.indexRegistry = indexRegistry;
        this.metrics = metricsFactory.getEurekaServerRegistryMetrics();

        internalStore = new ConcurrentHashMap<>();
        pauseableSubject = PauseableSubject.create();

        Observable.merge(registrationSubject).observeOn(scheduler).subscribe(
                new Subscriber<ChangeNotification<InstanceInfoWithSource>>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Registration subject onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Registration subject terminated with an error", e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfoWithSource> notification) {
                        InstanceInfo instanceInfo = notification.getData().getInstanceInfo();
                        Source source = notification.getData().getSource();

                        // Duplicate registration detection (last subscription wins).
                        String clientId = clientIdFor(instanceInfo.getId(), source);
                        Source latestSource = approvedRegistrations.get(clientId);
                        if (latestSource == null || !latestSource.equals(source)) {
                            return;
                        }

                        NotifyingInstanceInfoHolder holder = internalStore.get(instanceInfo.getId());
                        switch (notification.getKind()) {
                            case Add:
                                if (holder == null) {
                                    holder = new NotifyingInstanceInfoHolder(pauseableSubject, instanceInfo.getId(), metrics);
                                    internalStore.put(instanceInfo.getId(), holder);
                                    metrics.setRegistrySize(internalStore.size());
                                }
                                holder.update(source, instanceInfo);
                                break;
                            case Delete:
                                if (holder != null && holder.remove(source)) {
                                    internalStore.remove(instanceInfo.getId());
                                    metrics.setRegistrySize(internalStore.size());
                                }
                                approvedRegistrations.remove(clientId, source);
                                break;
                            default:
                                logger.error("Unexpected notification type {}", notification.getKind());
                        }
                    }
                }
        );
    }

    // -------------------------------------------------
    // Registry manipulation
    // -------------------------------------------------

    @Override
    public Observable<Void> register(final String id, final Observable<InstanceInfo> registrationUpdates, final Source source) {
        return Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                Observable<ChangeNotification<InstanceInfoWithSource>> changeNotifications =
                        toSourcedChangeNotificationStream(id, registrationUpdates, source)
                                .doOnSubscribe(new Action0() {
                                    @Override
                                    public void call() {
                                        approvedRegistrations.put(clientIdFor(id, source), source);
                                    }
                                });
                registrationSubject.onNext(changeNotifications);
                subscriber.onCompleted();
            }
        });
    }

    private static String clientIdFor(String id, Source source) {
        return source.getOrigin() + "/" + source.getName() + '/' + id;
    }

    @Override
    public Observable<Boolean> register(final InstanceInfo instanceInfo, final Source source) {
        Observable<ChangeNotification<InstanceInfoWithSource>> registerObservable = Observable.just(
                new ChangeNotification<>(Kind.Add, new InstanceInfoWithSource(instanceInfo, source))
        ).doOnSubscribe(new Action0() {
            @Override
            public void call() {
                approvedRegistrations.put(clientIdFor(instanceInfo.getId(), source), source);
            }
        });
        registrationSubject.onNext(registerObservable);
        return Observable.just(true);
    }

    @Override
    public Observable<Boolean> unregister(final InstanceInfo instanceInfo, final Source source) {
        Observable<ChangeNotification<InstanceInfoWithSource>> unregisterObservable = Observable.just(
                new ChangeNotification<>(Kind.Delete, new InstanceInfoWithSource(instanceInfo, source))
        );
        registrationSubject.onNext(unregisterObservable);
        return Observable.just(true);
    }

    @Override
    public int size() {
        return internalStore.size();
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

    @Override
    public Observable<InstanceInfo> forSnapshot(final Interest<InstanceInfo> interest, final Source.SourceMatcher sourceMatcher) {
        return forSnapshot(interest).filter(new Func1<InstanceInfo, Boolean>() {
            @Override
            public Boolean call(InstanceInfo instanceInfo) {
                MultiSourcedDataHolder<InstanceInfo> holder = internalStore.get(instanceInfo.getId());
                return holder != null && sourceMatcher.match(holder.getSource());
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
            // It is possible that the same instanceinfo will be both in snapshot and paused notification queue.
            pauseableSubject.pause(); // Pause notifications till we get a snapshot of current registry (registry.values())
            if (interest instanceof MultipleInterests) {
                return indexRegistry.forCompositeInterest((MultipleInterests) interest, this);
            } else {
                return indexRegistry.forInterest(interest, pauseableSubject,
                        new InstanceInfoInitStateHolder(getSnapshotForInterest(interest), interest));
            }
        } finally {
            pauseableSubject.resume();
        }
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, final Source.SourceMatcher sourceMatcher) {
        return forInterest(interest).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                if (changeNotification instanceof Sourced) {
                    Source notificationSource = ((Sourced) changeNotification).getSource();
                    return sourceMatcher.match(notificationSource);
                } else if (changeNotification instanceof StreamStateNotification) {
                    return false;
                } else {
                    logger.warn("Received notification without a source, {}", changeNotification);
                    return false;
                }
            }
        });
    }

    @Override
    public Observable<Long> evictAllExcept(final Source.SourceMatcher retainMatcher) {
        long count = 0;
        for (MultiSourcedDataHolder<InstanceInfo> holder : internalStore.values()) {
            for (Source source : holder.getAllSources()) {
                if (!retainMatcher.match(source)) {
                    unregister(holder.get(source), source);
                    count++;
                }
            }
        }
        logger.info("Completed evicting registry with source retain matcher {}; removed {} copies", retainMatcher, count);
        return Observable.just(count);
    }

    @Override
    public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
        return Observable.from(internalStore.values());
    }

    @Override
    public Observable<Void> shutdown() {
        logger.info("Shutting down the eureka registry");

        registrationSubject.onCompleted();
        pauseableSubject.onCompleted();
        internalStore.clear();
        return indexRegistry.shutdown();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        registrationSubject.onCompleted();
        pauseableSubject.onCompleted();
        return indexRegistry.shutdown(cause);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SourcedEurekaRegistryImpl{\n");
        for (NotifyingInstanceInfoHolder holder : internalStore.values()) {
            sb.append(holder.toStringSummary()).append(",\n");
        }
        sb.append('}');
        return sb.toString();
    }

    private Iterator<ChangeNotification<InstanceInfo>> getSnapshotForInterest(final Interest<InstanceInfo> interest) {
        final Collection<NotifyingInstanceInfoHolder> eurekaHolders = internalStore.values();
        return new FilteredIterator(interest, eurekaHolders.iterator());
    }

    private static class FilteredIterator implements Iterator<ChangeNotification<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;
        private final Iterator<NotifyingInstanceInfoHolder> delegate;
        private ChangeNotification<InstanceInfo> next;

        private FilteredIterator(Interest<InstanceInfo> interest, Iterator<NotifyingInstanceInfoHolder> delegate) {
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
}

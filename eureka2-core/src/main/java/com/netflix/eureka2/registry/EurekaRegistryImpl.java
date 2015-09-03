package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.utils.functions.BufferMarkerMergeFunctions;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.index.IndexRegistry;
import com.netflix.eureka2.registry.index.IndexRegistryImpl;
import com.netflix.eureka2.registry.index.InstanceInfoInitStateHolder;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.utils.ExtCollections;
import com.netflix.eureka2.utils.rx.PauseableSubject;
import com.netflix.eureka2.utils.functions.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.AsyncSubject;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author David Liu
 */
public class EurekaRegistryImpl implements EurekaRegistry<InstanceInfo> {
    private static final Logger logger = LoggerFactory.getLogger(EurekaRegistryImpl.class);

    protected final MultiSourcedDataStore<InstanceInfo> internalStore;
    protected final IndexRegistry<InstanceInfo> indexRegistry;
    protected final PauseableSubject<ChangeNotification<InstanceInfo>> registryChangeSubject;  // subject for all changes in the registry
    protected final Scheduler.Worker worker;  // worker to schedule on for all work to the internal datastores
    protected final Source localSource = new Source(Source.Origin.LOCAL);
    protected final EurekaRegistryMetrics metrics;

    private final BufferMarkerMergeFunctions bufferMergeFunc = new BufferMarkerMergeFunctions(logger);

    @Inject
    public EurekaRegistryImpl(EurekaRegistryMetricFactory metricFactory) {
        this(
                new SimpleInstanceInfoDataStore(metricFactory.getEurekaServerRegistryMetrics()),
                new IndexRegistryImpl<InstanceInfo>(),
                metricFactory,
                Schedulers.computation()
        );
    }

    public EurekaRegistryImpl(IndexRegistry<InstanceInfo> indexRegistry,
                              EurekaRegistryMetricFactory metricFactory,
                              Scheduler scheduler) {
        this(
                new SimpleInstanceInfoDataStore(metricFactory.getEurekaServerRegistryMetrics()),
                indexRegistry,
                metricFactory,
                scheduler);
    }

    public EurekaRegistryImpl(MultiSourcedDataStore<InstanceInfo> internalStore,
                              IndexRegistry<InstanceInfo> indexRegistry,
                              EurekaRegistryMetricFactory metricFactory,
                              Scheduler scheduler) {
        this.internalStore = internalStore;
        this.indexRegistry = indexRegistry;
        this.worker = scheduler.createWorker();
        this.registryChangeSubject = PauseableSubject.create();

        this.metrics = metricFactory.getEurekaServerRegistryMetrics();
    }

    @Override
    public Observable<Void> shutdown() {
        logger.info("Shutting down the eureka registry");

        worker.unsubscribe();
        registryChangeSubject.onCompleted();
        return internalStore.shutdown().mergeWith(indexRegistry.shutdown());
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        logger.error("Shutting down the eureka registry due to error", cause);

        worker.unsubscribe();
        registryChangeSubject.onCompleted();
        return internalStore.shutdown(cause).mergeWith(indexRegistry.shutdown(cause));
    }

    /**
     * Assume single threaded access
     */
    private void processNotification(ChangeNotification<InstanceInfo> notification, Source source) {
        try {
            if (notification.isDataNotification()) {
                InstanceInfo instanceInfo = notification.getData();

                switch (notification.getKind()) {
                    case Add:
                    case Modify:
                        ChangeNotification<InstanceInfo>[] notifications = internalStore.update(instanceInfo, source);
                        if (notifications.length != 0) {
                            metrics.setRegistrySize(internalStore.size());
                            for (ChangeNotification<InstanceInfo> n : notifications) {
                                registryChangeSubject.onNext(n);
                            }
                        }
                        break;
                    case Delete:
                        notifications = internalStore.remove(instanceInfo.getId(), source);
                        if (notifications.length != 0) {
                            metrics.setRegistrySize(internalStore.size());
                            for (ChangeNotification<InstanceInfo> n : notifications) {
                                registryChangeSubject.onNext(n);
                            }
                        }
                        break;
                    default:
                        logger.error("Unexpected notification type {}", notification.getKind());
                }
            } else {  // is streamStateNotification
                registryChangeSubject.onNext(notification);
            }
        } catch (Exception e) {
            logger.error("Error processing the notification in the registry: {}", notification, e);
        }
    }

    @Override
    public Observable<Void> connect(final Source source, Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
        return registrationUpdates
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(final ChangeNotification<InstanceInfo> notification) {
                        worker.schedule(new Action0() {
                            @Override
                            public void call() {
                                processNotification(notification, source);
                            }
                        });
                    }
                })
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        logger.info("Stream from {} onCompleted", source);
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.info("Stream from {} onErrored", source);
                    }
                })
                .ignoreElements()
                .cast(Void.class)
                .share();
    }

    @Override
    public Observable<Long> evictAll(final Source.SourceMatcher evictionMatcher) {
        final AsyncSubject<Long> evictionResult = AsyncSubject.create();

        final AtomicLong count = new AtomicLong(0);
        for (final MultiSourcedDataHolder<InstanceInfo> holder : internalStore.values()) {
            for (final Source source : holder.getAllSources()) {
                if (evictionMatcher.match(source)) {
                    worker.schedule(new Action0() {
                        @Override
                        public void call() {
                            metrics.setRegistrySize(internalStore.size());
                            for (ChangeNotification<InstanceInfo> notification : internalStore.remove(holder.get(source).getId(), source)) {
                                registryChangeSubject.onNext(notification);
                            }
                            metrics.setRegistrySize(internalStore.size());
                            evictionResult.onNext(count.incrementAndGet());
                        }
                    });
                }
            }
        }

        // finally schedule an action to onComplete the evictionResult
        worker.schedule(new Action0() {
            @Override
            public void call() {
                logger.info("Completed evicting registry with source eviction matcher {}; removed {} copies", evictionMatcher, count);
                evictionResult.onCompleted();
            }
        });

        return evictionResult;
    }

    @Override
    public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
        return Observable.from(internalStore.values());
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
        ChangeNotification<InstanceInfo> localBufferStart = new SourcedStreamStateNotification<>(StreamStateNotification.BufferState.BufferStart, interest, localSource);
        ChangeNotification<InstanceInfo> localBufferEnd = new SourcedStreamStateNotification<>(StreamStateNotification.BufferState.BufferEnd, interest, localSource);

        Iterator<ChangeNotification<InstanceInfo>> currentSnapshot = ExtCollections.concat(
                ExtCollections.singletonIterator(localBufferStart),
                getSnapshotForInterest(interest),
                ExtCollections.singletonIterator(localBufferEnd)
        );
        return forInterestBase(interest, currentSnapshot)
                .map(bufferMergeFunc.mergeDiffSources(interest, new Func1<Source, String>() {
                    @Override
                    public String call(Source source) {
                        return source.getOriginNamePair();
                    }
                }))
                .filter(RxFunctions.filterNullValuesFunc());
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, final Source.SourceMatcher sourceMatcher) {
        return forInterestBase(interest, getSnapshotForInterest(interest)).filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                if (changeNotification instanceof Sourced) {
                    Source notificationSource = ((Sourced) changeNotification).getSource();
                    return sourceMatcher.match(notificationSource);
                } else {
                    logger.warn("Received notification without a source, {}", changeNotification);
                    return false;
                }
            }
        });
    }

    private Observable<ChangeNotification<InstanceInfo>> forInterestBase(Interest<InstanceInfo> interest, Iterator<ChangeNotification<InstanceInfo>> currentSnapshot) {
        try {
            // TODO: this method can be run concurrently from different channels, unless we run everything on single server event loop.
            // It is possible that the same instanceinfo will be both in snapshot and paused notification queue.
            registryChangeSubject.pause(); // Pause notifications till we get a snapshot of current registry (registry.values())
            if (interest instanceof MultipleInterests) {
                return indexRegistry.forCompositeInterest((MultipleInterests) interest, this);
            } else {
                return indexRegistry.forInterest(interest, registryChangeSubject,
                        new InstanceInfoInitStateHolder(currentSnapshot, interest));
            }
        } finally {
            registryChangeSubject.resume();
        }
    }


    private Iterator<ChangeNotification<InstanceInfo>> getSnapshotForInterest(final Interest<InstanceInfo> interest) {
        final Collection<MultiSourcedDataHolder<InstanceInfo>> eurekaHolders = internalStore.values();
        return new FilteredIterator(interest, eurekaHolders.iterator());
    }

    @Override
    public Source getSource() {
        return localSource;
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

}

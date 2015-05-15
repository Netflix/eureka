package com.netflix.eureka2.client.interest;

import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Index.InitStateHolder;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.interests.SourcedModifyNotification;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;

/**
 * Index registry implementation that merges batching hints information from
 * external source (coming from InterestChannel), and its associated local Eureka registry.
 *
 * @author Tomasz Bak
 */
public class BatchAwareIndexRegistry<T> implements IndexRegistry<T> {

    private static final ChangeNotification<?> FINISH_BATCHING_NOTIFICATION = new ChangeNotification<>(Kind.BufferSentinel, null);

    private final IndexRegistry<T> delegateRegistry;
    private final BatchingRegistry<T> remoteBatchingRegistry;

    public BatchAwareIndexRegistry(IndexRegistry<T> delegateRegistry, BatchingRegistry<T> remoteBatchingRegistry) {
        this.delegateRegistry = delegateRegistry;
        this.remoteBatchingRegistry = remoteBatchingRegistry;
    }

    @Override
    public Observable<ChangeNotification<T>> forInterest(Interest<T> interest,
                                                         Observable<ChangeNotification<T>> dataSource,
                                                         InitStateHolder<T> initStateHolder) {
        return mergeWithBatchRegistryHints(interest, delegateRegistry.forInterest(interest, dataSource, initStateHolder));
    }

    @Override
    public Observable<ChangeNotification<T>> forCompositeInterest(MultipleInterests<T> interest,
                                                                  SourcedEurekaRegistry<T> registry) {
        return mergeWithBatchRegistryHints(interest, delegateRegistry.forCompositeInterest(interest, registry));
    }

    @Override
    public Observable<Void> shutdown() {
        remoteBatchingRegistry.shutdown();
        return delegateRegistry.shutdown();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        remoteBatchingRegistry.shutdown();
        return delegateRegistry.shutdown(cause);
    }

    private Observable<ChangeNotification<T>> mergeWithBatchRegistryHints(final Interest<T> interest,
                                                                          final Observable<ChangeNotification<T>> changeNotifications) {
        return Observable.create(new OnSubscribe<ChangeNotification<T>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<T>> subscriber) {
                ConnectableObservable<ChangeNotification<T>> notifications = changeNotifications.publish();
                final BatchingRegistryImpl<T> localBatchingRegistry = new BatchingRegistryImpl<>();
                localBatchingRegistry.connectTo(notifications);

                // Buffer is defined by merge of batch hint from the registry and the channel
                final AtomicBoolean batchingMode = new AtomicBoolean();
                Observable<ChangeNotification<T>> finishBatchingObservable = BatchFunctions.combine(
                        localBatchingRegistry.forInterest(interest),
                        remoteBatchingRegistry.forInterest(interest).doOnTerminate(new Action0() {
                            @Override
                            public void call() {
                                // Local registry will be shutdown on forInterest unsubscribe or
                                // when there is a global shutdown (here)
                                localBatchingRegistry.shutdown();
                            }
                        })
                ).doOnNext(new Action1<Boolean>() {
                    @Override
                    public void call(Boolean status) {
                        batchingMode.set(status);
                    }
                }).filter(new Func1<Boolean, Boolean>() {
                    @Override
                    public Boolean call(Boolean batching) {
                        return !batching;
                    }
                }).map(new Func1<Boolean, ChangeNotification<T>>() {
                    @Override
                    public ChangeNotification<T> call(Boolean aBoolean) {
                        return (ChangeNotification<T>) FINISH_BATCHING_NOTIFICATION;
                    }
                }).doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        localBatchingRegistry.shutdown();
                    }
                });

                // Plain data change notification stream
                Observable<ChangeNotification<T>> dataNotifications = notifications
                        .filter(ChangeNotifications.dataOnlyFilter())
                        .flatMap(new Func1<ChangeNotification<T>, Observable<ChangeNotification<T>>>() {
                            @Override
                            public Observable<ChangeNotification<T>> call(ChangeNotification<T> notification) {
                                ChangeNotification<T> result = notification;
                                if (notification instanceof SourcedChangeNotification) {
                                    result = ((SourcedChangeNotification<T>) notification).toBaseNotification();
                                }
                                if (notification instanceof SourcedModifyNotification) {
                                    result = ((SourcedModifyNotification<T>) notification).toBaseNotification();
                                }
                                if (batchingMode.get()) {
                                    return Observable.just(result);
                                }
                                return Observable.just(result, (ChangeNotification<T>) FINISH_BATCHING_NOTIFICATION);
                            }
                        });

                finishBatchingObservable.mergeWith(dataNotifications).subscribe(subscriber);

                // Two subscribers are watching it: localBatchingRegistry and resultSubject
                notifications.connect();
            }
        });
    }
}

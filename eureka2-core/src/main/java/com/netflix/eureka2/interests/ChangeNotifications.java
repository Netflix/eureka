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

package com.netflix.eureka2.interests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.Observable.Operator;
import rx.Observable.Transformer;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Collection of transformation functions operating on {@link ChangeNotification} data.
 *
 * @author Tomasz Bak
 */
public final class ChangeNotifications {

    private static final Func1<ChangeNotification<?>, Boolean> DATA_ONLY_FILTER_FUNC =
            new Func1<ChangeNotification<?>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<?> notification) {
                    return notification.isDataNotification();
                }
            };

    private static final Func1<ChangeNotification<?>, Boolean> STREAM_STATE_FILTER_FUNC =
            new Func1<ChangeNotification<?>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<?> notification) {
                    return notification instanceof StreamStateNotification;
                }
            };

    private static final Comparator<InstanceInfo> INSTANCE_INFO_IDENTITY_COMPARATOR = new Comparator<InstanceInfo>() {
        @Override
        public int compare(InstanceInfo o1, InstanceInfo o2) {
            return o1.getId().compareTo(o2.getId());
        }
    };

    private ChangeNotifications() {
    }

    public static Comparator<InstanceInfo> instanceInfoIdentityComparator() {
        return INSTANCE_INFO_IDENTITY_COMPARATOR;
    }

    public static <T> Observable<ChangeNotification<T>> from(T... values) {
        if (values == null || values.length == 0) {
            return Observable.empty();
        }
        List<ChangeNotification<T>> notifications = new ArrayList<>(values.length);
        for (T value : values) {
            notifications.add(new ChangeNotification<T>(Kind.Add, value));
        }
        return Observable.from(notifications);
    }

    public static Func1<ChangeNotification<?>, Boolean> dataOnlyFilter() {
        return DATA_ONLY_FILTER_FUNC;
    }

    public static Func1<ChangeNotification<?>, Boolean> streamStateFilter() {
        return STREAM_STATE_FILTER_FUNC;
    }

    /**
     * Given a list of {@link ChangeNotification}s, return the final set of values received after
     * applying those notifications.
     */
    public static <T> SortedSet<T> evaluate(List<ChangeNotification<T>> notifications, Comparator<T> identityComparator) {
        List<ChangeNotification<T>> collapsed = collapse(notifications, identityComparator);
        TreeSet<T> result = new TreeSet<T>(identityComparator);
        for (ChangeNotification<T> item : collapsed) {
            if (item.getKind() == Kind.Add || item.getKind() == Kind.Modify) {
                result.add(item.getData());
            }
        }
        return result;
    }

    /**
     * Convert change notification stream with buffering start/end markers into stream of lists, where each
     * list element contains a batch of data delineated by the markers. Only non-empty lists are
     * issued, which means that for two successive BufferSentinels from the stream, the second
     * one will be swallowed.
     *
     * @return observable of non-empty list objects
     */
    public static <T> Transformer<ChangeNotification<T>, List<ChangeNotification<T>>> delineatedBuffers() {
        return new Transformer<ChangeNotification<T>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<ChangeNotification<T>> notifications) {
                final AtomicBoolean bufferStart = new AtomicBoolean();
                final AtomicReference<List<ChangeNotification<T>>> bufferRef = new AtomicReference<>();
                return notifications.map(new Func1<ChangeNotification<T>, List<ChangeNotification<T>>>() {
                    @Override
                    public List<ChangeNotification<T>> call(ChangeNotification<T> notification) {
                        List<ChangeNotification<T>> buffer = bufferRef.get();
                        if (notification instanceof StreamStateNotification) {
                            BufferState bufferState = ((StreamStateNotification<T>) notification).getBufferState();
                            if (bufferState == BufferState.BufferStart) {
                                bufferStart.set(true);
                            } else { // BufferEnd
                                bufferStart.set(false);
                                bufferRef.set(null);
                                return buffer;
                            }
                        } else if (bufferStart.get()) {
                            if (buffer == null) {
                                bufferRef.set(buffer = new ArrayList<ChangeNotification<T>>());
                            }
                            buffer.add(notification);
                        } else {
                            return Collections.singletonList(notification);
                        }

                        return null;
                    }
                }).filter(RxFunctions.filterNullValuesFunc());
            }
        };
    }

    /**
     * Collapse observable of change notification batches into a set of currently known items.
     *
     * @return observable of distinct set objects
     */
    public static <T> Transformer<List<ChangeNotification<T>>, Set<T>> snapshots() {
        final Set<T> snapshotSet = new HashSet<>();
        return new Transformer<List<ChangeNotification<T>>, Set<T>>() {
            @Override
            public Observable<Set<T>> call(Observable<List<ChangeNotification<T>>> batches) {
                return batches.map(new Func1<List<ChangeNotification<T>>, Set<T>>() {
                    @Override
                    public Set<T> call(List<ChangeNotification<T>> batch) {
                        boolean changed = false;
                        for (ChangeNotification<T> item : batch) {
                            switch (item.getKind()) {
                                case Add:
                                case Modify:
                                    changed |= snapshotSet.add(item.getData());
                                    break;
                                case Delete:
                                    changed |= snapshotSet.remove(item.getData());
                                    break;
                            }
                        }
                        if (changed) {
                            return new HashSet<>(snapshotSet);
                        }
                        return null;
                    }
                }).filter(RxFunctions.filterNullValuesFunc());
            }
        };
    }

    /**
     * Given list of change notifications collapse changes for each instance to its final value. For example
     * if there are changes for instance A {Add, Modify, Delete, Add}, they are collapsed to single
     * { Add } notification. The following rules are applied:
     * <ul>
     *     <li>{ Add, Delete } = { Delete }</li>
     *     <li>{ Add, Modify } = { Add }</li>
     *     <li>{ Modify, Add } = { Add }</li>
     *     <li>{ Modify, Delete } = { Delete }</li>
     *     <li>{ Delete, Add } = { Add }</li>
     *     <li>{ Delete, Modify } = { Modify }</li>
     * </ul>
     */
    public static <T> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> collapse(final Comparator<T> identityComparator) {
        return new Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<ChangeNotification<T>>> listObservable) {
                return listObservable.map(new Func1<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
                    @Override
                    public List<ChangeNotification<T>> call(List<ChangeNotification<T>> notifications) {
                        return collapse(notifications, identityComparator);
                    }
                });
            }

        };
    }

    /**
     * Given list of change notifications collapse changes for each instance to its final value. For example
     * if there are changes for instance A {Add, Modify, Delete, Add}, they are collapsed to single
     * Add notification. The following rules are applied:
     * <ul>
     *     <li>{ Add, Delete } = { Delete }</li>
     *     <li>{ Add, Modify } = { Add }</li>
     *     <li>{ Modify, Add } = { Add }</li>
     *     <li>{ Modify, Delete } = { Delete }</li>
     *     <li>{ Delete, Add } = { Add }</li>
     *     <li>{ Delete, Modify } = { Modify }</li>
     * </ul>
     */
    public static <T> Transformer<List<List<ChangeNotification<T>>>, List<ChangeNotification<T>>> collapseLists(final Comparator<T> identityComparator) {
        return new Transformer<List<List<ChangeNotification<T>>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<List<ChangeNotification<T>>>> listOfListObservable) {
                return listOfListObservable.map(new Func1<List<List<ChangeNotification<T>>>, List<ChangeNotification<T>>>() {
                    @Override
                    public List<ChangeNotification<T>> call(List<List<ChangeNotification<T>>> notificationLists) {
                        Map<T, Integer> markers = new TreeMap<>(identityComparator);
                        List<ChangeNotification<T>> result = new ArrayList<ChangeNotification<T>>();
                        for (int i = notificationLists.size() - 1; i >= 0; i--) {
                            collapse(notificationLists.get(i), markers, result);
                        }
                        Collections.reverse(result);
                        return result;
                    }
                });
            }
        };
    }

    /**
     * Aggregate change notifications in the specified time intervals collapsing changes pertaining to same
     * data objects.
     */
    public static <T> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> aggregateChanges(
            final Comparator<T> identityComparator, final long interval, final TimeUnit timeUnit, final Scheduler scheduler) {
        return new Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<ChangeNotification<T>>> batchUpdates) {
                return batchUpdates.buffer(interval, timeUnit, scheduler).compose(collapseLists(identityComparator));
            }
        };
    }

    public static <T> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> aggregateChanges(
            final Comparator<T> identityComparator, final long interval, final TimeUnit timeUnit) {
        return aggregateChanges(identityComparator, interval, timeUnit, Schedulers.computation());
    }

    /**
     * It is a version of {@link #aggregateChanges} method, where the first item is emitted eagerly, and aggregation
     * is applied only afterwards. This fits the Eureka registration model, where first batch will contain the full
     * current registry content, which is followed by live updates.
     */
    public static <T> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> emitAndAggregateChanges(
            final Comparator<T> identityComparator, final long interval, final TimeUnit timeUnit, final Scheduler scheduler) {
        return new Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(final Observable<List<ChangeNotification<T>>> batchUpdates) {
                return batchUpdates.lift(new Operator<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
                    @Override
                    public Subscriber<? super List<ChangeNotification<T>>> call(final Subscriber<? super List<ChangeNotification<T>>> subscriber) {
                        final AtomicBoolean first = new AtomicBoolean();
                        final PublishSubject<List<ChangeNotification<T>>> aggregateSubject = PublishSubject.create();
                        return new Subscriber<List<ChangeNotification<T>>>() {
                            @Override
                            public void onCompleted() {
                                subscriber.onCompleted();
                            }

                            @Override
                            public void onError(Throwable e) {
                                subscriber.onError(e);
                            }

                            @Override
                            public void onNext(List<ChangeNotification<T>> notifications) {
                                if (!first.get()) {
                                    subscriber.onNext(collapse(notifications, identityComparator));

                                    first.set(true);
                                    Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> transformer =
                                            aggregateChanges(identityComparator, interval, timeUnit, scheduler);
                                    aggregateSubject.compose(transformer).subscribe(subscriber);
                                } else {
                                    aggregateSubject.onNext(notifications);
                                }
                            }
                        };
                    }
                });
            }
        };
    }

    public static <T> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> emitAndAggregateChanges(
            final Comparator<T> identityComparator, final long interval, final TimeUnit timeUnit) {
        return emitAndAggregateChanges(identityComparator, interval, timeUnit, Schedulers.computation());
    }

    private static <T> List<ChangeNotification<T>> collapse(List<ChangeNotification<T>> notifications, Comparator<T> identityComparator) {
        List<ChangeNotification<T>> result = new ArrayList<>();
        collapse(notifications, new TreeMap<T, Integer>(identityComparator), result);
        Collections.reverse(result);
        return result;
    }

    private static <T> void collapse(List<ChangeNotification<T>> notifications, Map<T, Integer> markers, List<ChangeNotification<T>> result) {
        for (int i = notifications.size() - 1; i >= 0; i--) {
            ChangeNotification<T> next = notifications.get(i);
            T data = next.getData();
            if (markers.keySet().contains(data)) {
                int idx = markers.get(data);
                if (next.getKind() == Kind.Add && result.get(idx).getKind() == Kind.Modify) {
                    result.set(idx, next);
                }
            } else {
                markers.put(data, result.size());
                result.add(next);
            }
        }
    }
}

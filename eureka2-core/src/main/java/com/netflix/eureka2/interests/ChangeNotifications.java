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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Notification;
import rx.Observable;
import rx.Observable.Transformer;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

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

    private static final Identity<InstanceInfo, String> INSTANCE_INFO_IDENTITY = new Identity<InstanceInfo, String>() {
        @Override
        public String getId(InstanceInfo instanceInfo) {
            return instanceInfo.getId();
        }
    };

    private static final Identity<Server, String> SERVER_IDENTITY = new Identity<Server, String>() {
        @Override
        public String getId(Server server) {
            return server.getHost();
        }
    };

    private ChangeNotifications() {
    }

    public static Identity<InstanceInfo, String> instanceInfoIdentity() {
        return INSTANCE_INFO_IDENTITY;
    }

    public static Identity<Server, String> serverIdentity() {
        return SERVER_IDENTITY;
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
     * Return a function that maps data to Add ChangeNotification of the data.
     *
     * @param <T> type of the data to wrap in a ChangeNotification
     * @return an Add ChangeNotification of the data
     */
    public static <T> Func1<T, ChangeNotification<T>> toAddChangeNotification() {
        return new Func1<T, ChangeNotification<T>>() {
            @Override
            public ChangeNotification<T> call(T data) {
                return new ChangeNotification<>(Kind.Add, data);
            }
        };
    }

    /**
     * Given a list of {@link ChangeNotification}s:
     * <ul>
     *     <li>- collapse changes for same items (for example A{ Add Modify } -> A { Add }, B { Add, Delete } -> None )</li>
     *     <li>- take values from instance add/modify change notifications</li>
     *     <li>- combine all into set and return to the caller</li>
     * </ul>
     * <p>
     * For example, applying this method to list [ A{Add}, B{Add}, A{Modify}, B{Remove} ] will result in [A].
     *
     */
    public static <T, ID> LinkedHashSet<T> collapseAndExtract(List<ChangeNotification<T>> notifications, Identity<T, ID> identity) {
        List<ChangeNotification<T>> collapsed = collapse(notifications, identity);
        LinkedHashSet<T> result = new LinkedHashSet<>();
        for (ChangeNotification<T> notification : collapsed) {
            if (notification.getKind() == Kind.Add || notification.getKind() == Kind.Modify) {
                result.add(notification.getData());
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
                                bufferRef.set(buffer = new ArrayList<>());
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
     * Convert change notification stream with buffering sentinels into stream of lists, where each
     * list element contains a batch of data delineated by the markers. Only non-empty lists are
     * issued, which means that for two successive BufferSentinels from the stream, the second
     * one will be swallowed.
     *
     * an onComplete on the change notification stream is considered as another (the last) BufferSentinel.
     * an onError will not return any partial buffered data.
     *
     * @return observable of non-empty list objects
     */
    public static <T> Transformer<ChangeNotification<T>, List<ChangeNotification<T>>> buffers() {
        return new Transformer<ChangeNotification<T>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<ChangeNotification<T>> notifications) {
                final AtomicReference<List<ChangeNotification<T>>> bufferRef = new AtomicReference<>();
                bufferRef.set(new ArrayList<ChangeNotification<T>>());

                return notifications
                        .filter(RxFunctions.filterNullValuesFunc())
                        .materialize()
                                // concatMap as the internal observables all onComplete immediately
                        .concatMap(new Func1<Notification<ChangeNotification<T>>, Observable<List<ChangeNotification<T>>>>() {
                            @Override
                            public Observable<List<ChangeNotification<T>>> call(Notification<ChangeNotification<T>> rxNotification) {
                                List<ChangeNotification<T>> buffer = bufferRef.get();

                                switch (rxNotification.getKind()) {
                                    case OnNext:
                                        ChangeNotification<T> notification = rxNotification.getValue();
                                        if (notification.getKind() == Kind.BufferSentinel) {
                                            bufferRef.set(new ArrayList<ChangeNotification<T>>());
                                            return Observable.just(buffer);
                                        }
                                        buffer.add(notification);
                                        break;
                                    case OnCompleted:
                                        return Observable.just(buffer);  // OnCompleted == BufferSentinel
                                    case OnError:
                                        //clear the buffer and onError, don't return partial error buffer
                                        bufferRef.set(new ArrayList<ChangeNotification<T>>());
                                        return Observable.error(rxNotification.getThrowable());
                                }

                                return Observable.empty();
                            }
                        });
            }
        };
    }

    /**
     * Collapse observable of change notification batches into a set of currently known items.
     * Use a LinkedHashSet to maintain order based on insertion order.
     *
     * Note that the same batch can be emitted multiple times if the transformer receive "empty" prompts
     * from the buffers transformer. Users should apply .distinctUntilChanged() if this is not desired
     * behaviour.
     *
     * @return observable of distinct set objects
     */
    public static <T, ID> Transformer<List<ChangeNotification<T>>, LinkedHashSet<T>> snapshots(final Identity<T, ID> identity) {
        final LinkedHashMap<ID, T> snapshotMap = new LinkedHashMap<>();
        return new Transformer<List<ChangeNotification<T>>, LinkedHashSet<T>>() {
            @Override
            public Observable<LinkedHashSet<T>> call(Observable<List<ChangeNotification<T>>> batches) {
                return batches.map(new Func1<List<ChangeNotification<T>>, LinkedHashSet<T>>() {
                    @Override
                    public LinkedHashSet<T> call(List<ChangeNotification<T>> batch) {
                        for (ChangeNotification<T> item : batch) {
                            T data = item.getData();
                            switch (item.getKind()) {
                                case Add:
                                case Modify:
                                    // remove and re-add to update the insertion order
                                    snapshotMap.remove(identity.getId(data));
                                    snapshotMap.put(identity.getId(data), data);
                                    break;
                                case Delete:
                                    snapshotMap.remove(identity.getId(data));
                                    break;
                                default:
                                    // no-op
                            }
                        }
                        return new LinkedHashSet<T>(snapshotMap.values());
                    }
                });
            }
        };
    }

    /**
     * An Rx compose operator that given a list of change notifications, collapses changes for each instance
     * to its final value. The following rules are applied:
     * <ul>
     *     <li>{ Add, Delete } = { Delete }</li>
     *     <li>{ Add, Modify } = { Add }</li>
     *     <li>{ Modify, Add } = { Add }</li>
     *     <li>{ Modify, Delete } = { Delete }</li>
     *     <li>{ Delete, Add } = { Add }</li>
     *     <li>{ Delete, Modify } = { Modify }</li>
     * </ul>
     * <p>
     * For example if there is a change notification list [ A{Add}, B{Modify}, A{Modify}, B{Remove}, C{Remove} ],
     * applying this operator to the list will result in a new list [ A{Add}, B{Remove}, C{Remove}].
     */
    public static <T, ID> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> collapse(final Identity<T, ID> identity) {
        return new Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<ChangeNotification<T>>> listObservable) {
                return listObservable.map(new Func1<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
                    @Override
                    public List<ChangeNotification<T>> call(List<ChangeNotification<T>> notifications) {
                        return collapse(notifications, identity);
                    }
                });
            }

        };
    }

    /**
     * This is a variant of collapse operator (see {@link #collapse(Identity)}), that accepts nested lists of
     * change notifications. It is useful in scenarios where batches of change notifications are aggregated over
     * a specific amount of time, and must be ultimately collapsed. It is equivalent to joining sub lists into
     * single list and applying {@link #collapse(Identity)} transformation, but by combining both steps it is
     * more efficient.
     */
    public static <T, ID> Transformer<List<List<ChangeNotification<T>>>, List<ChangeNotification<T>>> collapseLists(final Identity<T, ID> identity) {
        return new Transformer<List<List<ChangeNotification<T>>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<List<ChangeNotification<T>>>> listOfListObservable) {
                return listOfListObservable.map(new Func1<List<List<ChangeNotification<T>>>, List<ChangeNotification<T>>>() {
                    @Override
                    public List<ChangeNotification<T>> call(List<List<ChangeNotification<T>>> notificationLists) {
                        LinkedHashMap<ID, ChangeNotification<T>> collapsed = new LinkedHashMap<>();
                        for (List<ChangeNotification<T>> notifications : notificationLists) {
                            collapse(notifications, identity, collapsed);
                        }
                        return new ArrayList<>(collapsed.values());
                    }
                });
            }
        };
    }

    /**
     * Aggregate change notifications in the specified time intervals collapsing changes pertaining to same
     * data objects. Applying this observable on the change notification stream will result in batching all
     * emitted items/delineated batches for a specific amount of time, after which all the accumulated data
     * are collapsed into single list of most recently visible updates per each data item.
     */
    public static <T, ID> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> aggregateChanges(
            final Identity<T, ID> identity, final long interval, final TimeUnit timeUnit, final Scheduler scheduler) {
        return new Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<ChangeNotification<T>>> batchUpdates) {
                return batchUpdates.buffer(interval, timeUnit, scheduler).compose(collapseLists(identity));
            }
        };
    }

    /**
     * It is a version of {@link #aggregateChanges} method, where the first item is emitted eagerly, and aggregation
     * is applied only afterwards. This fits the Eureka registration model, where first batch will contain the full
     * current registry content, which is followed by live updates.
     */
    public static <T, ID> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> emitAndAggregateChanges(
            final Identity<T, ID> identity, final long interval, final TimeUnit timeUnit, final Scheduler scheduler) {
        return new Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>>() {

            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<List<ChangeNotification<T>>> batchUpdates) {
                return batchUpdates.buffer(Observable.timer(0, interval, timeUnit, scheduler)).compose(collapseLists(identity));

            }
        };
    }

    /**
     * See {@link #emitAndAggregateChanges(Identity, long, TimeUnit, Scheduler)}.
     */
    public static <T, ID> Transformer<List<ChangeNotification<T>>, List<ChangeNotification<T>>> emitAndAggregateChanges(
            final Identity<T, ID> identity, final long interval, final TimeUnit timeUnit) {
        return emitAndAggregateChanges(identity, interval, timeUnit, Schedulers.computation());
    }

    private static <T, ID> List<ChangeNotification<T>> collapse(List<ChangeNotification<T>> notifications, Identity<T, ID> identity) {
        LinkedHashMap<ID, ChangeNotification<T>> collapsed = new LinkedHashMap<>();
        collapse(notifications, identity, collapsed);
        return new ArrayList<>(collapsed.values());
    }

    private static <T, ID> void collapse(List<ChangeNotification<T>> notifications, Identity<T, ID> identity, LinkedHashMap<ID, ChangeNotification<T>> collapsed) {
        for (ChangeNotification<T> notification : notifications) {
            T data = notification.getData();
            if (notification.isDataNotification()) {
                // remove and re-add the notification to update the insertion order
                collapsed.remove(identity.getId(data));
                collapsed.put(identity.getId(data), notification);
            }
        }
    }

    /**
     * Interface for a way to extract the id of an arbitrary data type
     *
     * @param <T> the type of the data to extract id from
     * @param <ID> the type of the id to return
     */
    public interface Identity<T, ID> {
        ID getId(T data);
    }
}

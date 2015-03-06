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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

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

    private ChangeNotifications() {
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

}

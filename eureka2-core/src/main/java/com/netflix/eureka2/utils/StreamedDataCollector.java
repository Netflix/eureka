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

package com.netflix.eureka2.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import com.netflix.eureka2.model.notification.ChangeNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public abstract class StreamedDataCollector<R> {

    public abstract List<R> latestSnapshot();

    public abstract void close();

    public static <R> StreamedDataCollector<R> from(Collection<R> collection) {
        final List<R> copy = Collections.unmodifiableList(new ArrayList<R>(collection));
        return new StreamedDataCollector<R>() {
            @Override
            public List<R> latestSnapshot() {
                return copy;
            }

            @Override
            public void close() {
                // No-op
            }
        };
    }

    public static <E, R> StreamedDataCollector<R> from(Observable<ChangeNotification<E>> notifications,
                                                    Func1<E, R> converter) {
        return new ChangeNotificationCollector<>(notifications, converter);
    }

    static class ChangeNotificationCollector<E, R> extends StreamedDataCollector<R> {

        private static final Logger logger = LoggerFactory.getLogger(ChangeNotificationCollector.class);

        private final ConcurrentSkipListSet<R> servers = new ConcurrentSkipListSet<>();
        private final Subscription subscription;

        ChangeNotificationCollector(Observable<ChangeNotification<E>> notifications,
                                    final Func1<E, R> converter) {
            subscription = notifications.subscribe(new Subscriber<ChangeNotification<E>>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable e) {
                    logger.error("Change notification stream terminated with error", e);
                }

                @Override
                public void onNext(ChangeNotification<E> notification) {
                    R converted = converter.call(notification.getData());
                    switch (notification.getKind()) {
                        case Add:
                        case Modify:
                            servers.add(converted);
                            break;
                        case Delete:
                            servers.remove(converted);
                    }
                }

            });
        }

        @Override
        public List<R> latestSnapshot() {
            if (subscription.isUnsubscribed()) {
                throw new IllegalStateException("change notification stream is closed");
            }
            return new ArrayList<R>(servers);
        }

        @Override
        public void close() {
            subscription.unsubscribe();
        }
    }
}

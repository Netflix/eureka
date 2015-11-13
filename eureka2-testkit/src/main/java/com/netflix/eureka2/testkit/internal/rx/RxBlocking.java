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

package com.netflix.eureka2.testkit.internal.rx;

import rx.Observable;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * It is a collection of helper classes for dealing in a synchronous way
 * with observables, where {@link rx.observables.BlockingObservable} falls short.
 *
 * @author Tomasz Bak
 */
public class RxBlocking {

    /**
     * Interface for getting a value from observable. The call can be blocked up
     * to the configured timeout value, which runs independent from when this method
     * is called.
     */
    public interface RxItem<T> {
        T item() throws TimeoutException, InterruptedException;
    }

    /**
     * Returns true if a given observable is completed.
     *
     * @throws RuntimeException if an observable is on error, with cause field storing the original exception.
     */
    public static <T> boolean isCompleted(int timeout, TimeUnit timeUnit, Observable<T> observable) {
        return !iteratorFrom(timeout, timeUnit, observable).hasNext();
    }

    /**
     * Return {@link RxItem} that will give first value of the provided observable or will
     * time out. The timeout is measured from the time this method is called.
     */
    public static <T> RxItem<T> firstFrom(long timeout, TimeUnit timeUnit, final Observable<T> observable) {
        return new SingleValueRxItem<T>(TimeUnit.MILLISECONDS.convert(timeout, timeUnit), observable.first());
    }

    /**
     * Return {@link RxItem} that will give list of first values of the provided observables or will
     * time out. The timeout is measured from the time this method is called.
     */
    public static <T> RxItem<List<T>> firstFromEach(long timeout, TimeUnit timeUnit, final Observable<T>... observables) {
        List<Observable<T>> firstItems = new ArrayList<Observable<T>>(observables.length);
        for (Observable<T> o : observables) {
            firstItems.add(o.first());
        }
        return new ListRxItem<T>(TimeUnit.MILLISECONDS.convert(timeout, timeUnit), firstItems);
    }

    /**
     * Iterator from observable, where each hasNext/next method calls is guarded by a timeout.
     */
    public static <T> Iterator<T> iteratorFrom(final long timeout, final TimeUnit timeUnit, final Observable<T> observable) {
        return new Iterator<T>() {

            private final Iterator<T> internalIt = observable.toBlocking().getIterator();

            private final AtomicReference<T> next = new AtomicReference<T>();
            private final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();

            @Override
            public boolean hasNext() {
                if (next.get() != null) {
                    return true;
                }
                if (errorRef.get() != null) {
                    if (errorRef.get() instanceof NoSuchElementException) {
                        return false;
                    }
                    throwError();
                }
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<T> future = executor.submit(new Callable<T>() {
                    @Override
                    public T call() throws Exception {
                        return internalIt.next();
                    }
                });
                try {
                    T value = future.get(timeout, timeUnit);
                    next.set(value);
                } catch (ExecutionException e) {
                    errorRef.set(e.getCause());
                    if (e.getCause() instanceof NoSuchElementException) {
                        return false;
                    }
                    throwError();
                } catch (Exception e) {
                    errorRef.set(e);
                    throwError();
                } finally {
                    executor.shutdown();
                }
                return true;
            }

            @Override
            public T next() {
                if (hasNext()) {
                    return next.getAndSet(null);
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new RuntimeException("not supported operation");
            }

            private void throwError() {
                Throwable error = errorRef.get();
                if (error instanceof NoSuchElementException) {
                    throw (NoSuchElementException) error;
                }
                throw new RuntimeException(error);
            }
        };
    }

    private static class SingleValueRxItem<T> implements RxItem<T> {

        private final long startTime = System.currentTimeMillis();
        private final long timeoutMillis;
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile Object value;

        private SingleValueRxItem(long timeoutMillis, Observable<T> observable) {
            this.timeoutMillis = timeoutMillis;
            observable.subscribe(new Subscriber<Object>() {
                @Override
                public void onCompleted() {
                }

                @Override
                public void onError(Throwable e) {
                    value = e;
                    latch.countDown();
                }

                @Override
                public void onNext(Object o) {
                    value = o;
                    latch.countDown();
                }
            });
        }

        @Override
        public T item() throws TimeoutException, InterruptedException {
            long waitingTime = Math.max(0, timeoutMillis - (System.currentTimeMillis() - startTime));
            if (latch.await(waitingTime, TimeUnit.MILLISECONDS)) {
                if (value instanceof Throwable) {
                    throw new RuntimeException((Throwable) value);
                }
                return (T) value;
            }
            throw new TimeoutException("timed out during waiting on observable's first elememt");
        }
    }

    private static class ListRxItem<T> implements RxItem<List<T>> {

        private final long startTime = System.currentTimeMillis();
        private final long timeoutMillis;
        private final CountDownLatch latch;
        private final Object[] values;

        private ListRxItem(long timeoutMillis, List<Observable<T>> firstItems) {
            this.timeoutMillis = timeoutMillis;
            latch = new CountDownLatch(firstItems.size());
            values = new Object[firstItems.size()];

            for (int i = 0; i < firstItems.size(); i++) {
                final int idx = i;
                firstItems.get(i).subscribe(new Subscriber<Object>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        synchronized (values) {
                            values[idx] = e;
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onNext(Object value) {
                        synchronized (values) {
                            values[idx] = value;
                        }
                        latch.countDown();
                    }
                });
            }
        }

        @Override
        public List<T> item() throws TimeoutException, InterruptedException {
            long waitingTime = Math.max(0, timeoutMillis - (System.currentTimeMillis() - startTime));
            if (latch.await(waitingTime, TimeUnit.MILLISECONDS)) {
                List<T> resultList = new ArrayList<T>(values.length);
                for (Object value : values) {
                    if (value instanceof Throwable) {
                        throw new RuntimeException((Throwable) value);
                    }
                    resultList.add((T) value);
                }
                return resultList;
            }
            throw new TimeoutException("timed out during waiting on observable's first elememt");
        }
    }
}

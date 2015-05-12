package com.netflix.eureka2.utils;

import java.util.concurrent.Callable;

import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * An abstract implementation that allows extending classes to be able to serialize operations without need for locking.
 *
 * @author Nitesh Kant
 */
public abstract class SerializedTaskInvoker {

    private final SerializedTaskInvokerMetrics metrics;
    private final Worker worker;

    protected SerializedTaskInvoker(SerializedTaskInvokerMetrics metrics) {
        this(metrics, Schedulers.computation());
    }

    protected SerializedTaskInvoker(SerializedTaskInvokerMetrics metrics, Scheduler scheduler) {
        this.worker = scheduler.createWorker();
        this.metrics = metrics;
    }

    protected Observable<Void> submitForAck(final Callable<Observable<Void>> task) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(final Subscriber<? super Void> subscriber) {
                worker.schedule(new Action0() {
                    @Override
                    public void call() {
                        try {
                            task.call().subscribe(new Subscriber<Void>() {
                                @Override
                                public void onCompleted() {
                                    metrics.incrementOutputSuccess();
                                    subscriber.onCompleted();
                                }

                                @Override
                                public void onError(Throwable e) {
                                    metrics.incrementOutputFailure();
                                    subscriber.onError(e);
                                }

                                @Override
                                public void onNext(Void aVoid) {
                                    // No-op
                                }
                            });
                        } catch (Exception e) {
                            metrics.incrementOutputFailure();
                            subscriber.onError(e);
                        }
                    }
                });
            }
        });
    }

    protected <T> Observable<T> submitForResult(final Callable<Observable<T>> task) {
        return Observable.create(new Observable.OnSubscribe<T>() {
            @Override
            public void call(final Subscriber<? super T> subscriber) {
                worker.schedule(new Action0() {
                    @Override
                    public void call() {
                        try {
                            task.call().subscribe(new Subscriber<T>() {
                                @Override
                                public void onCompleted() {
                                    metrics.incrementOutputSuccess();
                                    subscriber.onCompleted();
                                }

                                @Override
                                public void onError(Throwable e) {
                                    metrics.incrementOutputFailure();
                                    subscriber.onError(e);
                                }

                                @Override
                                public void onNext(T value) {
                                    subscriber.onNext(value);
                                }
                            });
                        } catch (Exception e) {
                            metrics.incrementOutputFailure();
                            subscriber.onError(e);
                        }
                    }
                });
            }
        });
    }

    protected void shutdown() {
        worker.unsubscribe();
    }
}

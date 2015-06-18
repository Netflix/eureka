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
 * Associated metrics give insight into scheduled task queue length, and active tasks count.
 *
 * @author Nitesh Kant
 */
public abstract class SerializedTaskInvoker {

    private final SerializedTaskInvokerMetrics metrics;
    private final Worker worker;

    private final Action0 incrementSubscribedTaskFun = new Action0() {
        @Override
        public void call() {
            metrics.incrementRunningTasks();
        }
    };
    private final Action0 decrementSubscribedTaskFun = new Action0() {
        @Override
        public void call() {
            metrics.decrementRunningTasks();
        }
    };

    protected SerializedTaskInvoker(SerializedTaskInvokerMetrics metrics) {
        this(metrics, Schedulers.computation());
    }

    protected SerializedTaskInvoker(SerializedTaskInvokerMetrics metrics, Scheduler scheduler) {
        this.worker = scheduler.createWorker();
        this.metrics = metrics;
    }

    protected Observable<Void> submitForAck(final Callable<Observable<Void>> task) {
        return submitForResult(task);
    }

    protected <T> Observable<T> submitForResult(final Callable<Observable<T>> task) {
        return Observable.create(new Observable.OnSubscribe<T>() {
            @Override
            public void call(final Subscriber<? super T> subscriber) {
                metrics.incrementSchedulerTaskQueue();
                worker.schedule(new Action0() {
                    @Override
                    public void call() {
                        metrics.decrementSchedulerTaskQueue();
                        try {
                            task.call()
                                    .doOnSubscribe(incrementSubscribedTaskFun)
                                    .doOnUnsubscribe(decrementSubscribedTaskFun)
                                    .subscribe(new Subscriber<T>() {
                                        @Override
                                        public void onCompleted() {
                                            subscriber.onCompleted();
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            subscriber.onError(e);
                                        }

                                        @Override
                                        public void onNext(T value) {
                                            subscriber.onNext(value);
                                        }
                                    });
                        } catch (Exception e) {
                            subscriber.onError(e);
                        }
                    }
                });
            }
        });
    }

    protected void shutdownTaskInvoker() {
        worker.unsubscribe();
    }
}

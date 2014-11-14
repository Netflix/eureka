package com.netflix.eureka2.utils;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * An abstract implementation that allows extending classes to be able to serialize operations without need for locking.
 *
 * @author Nitesh Kant
 */
public abstract class SerializedTaskInvoker {

    private static final Logger logger = LoggerFactory.getLogger(SerializedTaskInvoker.class);

    private static final Exception TASK_CANCELLED = new CancellationException("Task cancelled");

    private final Worker worker;
    private final ConcurrentLinkedDeque<InvokerTask<?, ?>> taskQueue = new ConcurrentLinkedDeque<>();

    private final AtomicBoolean executorScheduled = new AtomicBoolean();
    private final Action0 executeAction = new Action0() {
        @Override
        public void call() {
            executorScheduled.set(false);
            while (!taskQueue.isEmpty()) {
                InvokerTask<?, ?> task = taskQueue.poll();
                try {
                    task.execute();
                } catch (Exception e) {
                    logger.error("Task execution failure", e);
                    task.cancel();
                }
            }
        }
    };

    protected SerializedTaskInvoker() {
        this(Schedulers.computation());
    }

    protected SerializedTaskInvoker(Scheduler scheduler) {
        this.worker = scheduler.createWorker();
    }

    protected Observable<Void> submitForAck(final Callable<Observable<Void>> task) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                addAndSchedule(new InvokerTaskWithAck(task, subscriber));
            }
        });
    }

    protected <T> Observable<T> submitForResult(final Callable<Observable<T>> task) {
        return Observable.create(new Observable.OnSubscribe<Observable<T>>() {
            @Override
            public void call(Subscriber<? super Observable<T>> subscriber) {
                addAndSchedule(new InvokerTaskWithResult<>(task, subscriber));
            }
        }).switchMap(new Func1<Observable<T>, Observable<? extends T>>() {
            @Override
            public Observable<? extends T> call(Observable<T> tObservable) {
                return tObservable;
            }
        });
    }

    private void addAndSchedule(InvokerTask<?, ?> invokerTask) {
        taskQueue.add(invokerTask);
        if (executorScheduled.compareAndSet(false, true)) {
            worker.schedule(executeAction);
        }
    }

    protected void shutdown() {
        worker.unsubscribe();
        while (!taskQueue.isEmpty()) {
            taskQueue.poll().cancel();
        }
    }

    private abstract static class InvokerTask<T, R> {

        protected final Callable<Observable<T>> actual;
        protected final Subscriber<? super R> subscriberForThisTask;

        private InvokerTask(Callable<Observable<T>> actual, Subscriber<? super R> subscriberForThisTask) {
            this.actual = actual;
            this.subscriberForThisTask = subscriberForThisTask;
        }

        protected abstract void execute();

        protected void cancel() {
            subscriberForThisTask.onError(TASK_CANCELLED);
        }
    }

    private class InvokerTaskWithAck extends InvokerTask<Void, Void> {

        private InvokerTaskWithAck(Callable<Observable<Void>> actual, Subscriber<? super Void> subscriberForThisTask) {
            super(actual, subscriberForThisTask);
        }

        @Override
        protected void execute() {
            try {
                actual.call().firstOrDefault(null).ignoreElements()
                        .doOnError(new Action1<Throwable>() {
                            @Override
                            public void call(Throwable e) {
                                subscriberForThisTask.onError(e);
                            }
                        })
                        .doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                subscriberForThisTask.onCompleted();
                            }
                        })
                        .subscribe();
            } catch (Throwable e) {
                logger.error("Exception invoking the InvokerTaskWithAck task.", e);
                subscriberForThisTask.onError(e);
            }
        }
    }

    private class InvokerTaskWithResult<T> extends InvokerTask<T, Observable<T>> {

        private InvokerTaskWithResult(Callable<Observable<T>> actual, Subscriber<? super Observable<T>> subscriberForThisTask) {
            super(actual, subscriberForThisTask);
        }

        @Override
        protected void execute() {
            try {
                subscriberForThisTask.onNext(actual.call());
                subscriberForThisTask.onCompleted();
            } catch (Throwable e) {
                logger.error("Exception invoking the InvokerTaskWithResult task.", e);
                subscriberForThisTask.onError(e);
            }
        }
    }
}

package com.netflix.eureka.client.service;

import com.netflix.eureka.service.ServiceChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.internal.operators.NotificationLite;
import rx.observers.SerializedSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.Subject;

import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An abstract implementation to serialize access to {@link ServiceChannel} for the client.
 *
 * @author Nitesh Kant
 */
/* pkg private: Channel invokers are always decorators for the actual channel */ abstract class AbstractChannelInvoker {

    private static final Logger logger = LoggerFactory.getLogger(AbstractChannelInvoker.class);

    private final QueueSubject taskSubject = QueueSubject.create();
    private final Subscription taskSubscription;

    protected AbstractChannelInvoker() {
        taskSubscription = taskSubject.subscribeOn(Schedulers.io()) /*Since subscription blocks on the underlying queue.*/
                .subscribe(new Subscriber<InvokerTask>() {

                    @Override
                    public void onCompleted() {
                        logger.debug("Channel invoker subscription complete. No more tasks will be invoked on this channel.");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("Error to channel invoker task subscriber. No more tasks will be invoked on this channel.", e);
                    }

                    @Override
                    public void onNext(InvokerTask invokerTask) {
                        invokerTask.execute(); // blocks till the task completes (Observable<Void> returned by task completes)
                    }
                });
    }

    protected Observable<Void> submitForAck(final Callable<Observable<Void>> task) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                taskSubject.onNext(new InvokerTaskWithAck(task, subscriber));
            }
        });
    }

    protected <T> Observable<T> submitForResult(final Callable<Observable<T>> task) {
        return Observable.create(new Observable.OnSubscribe<Observable<T>>() {
            @Override
            public void call(Subscriber<? super Observable<T>> subscriber) {
                taskSubject.onNext(new InvokerTaskWithResult<>(task, subscriber));
            }
        }).switchMap(new Func1<Observable<T>, Observable<? extends T>>() {
            @Override
            public Observable<? extends T> call(Observable<T> tObservable) {
                return tObservable;
            }
        });
    }

    protected void shutdown() {
        taskSubject.onCompleted();
        taskSubscription.unsubscribe();
    }

    /**
     * A {@link Subject} implementation which relays all notifications via an internal queue.
     *
     * <b>This must always be subscribed using {@link Observable#subscribeOn(Scheduler)} as the subscription call
     * blocks.</b>
     *
     * <h2>Can we use {@link SerializedSubscriber} instead</h2>
     * We want to strictly serialize these tasks, some of which ({@link InvokerTaskWithAck}) return an
     * acknowledgment which needs to be completed before we can execute the next task. The easiest way to do this is
     * by blocking the onNext() from this subject. Since {@link SerializedSubscriber} passes through the onNext when
     * there isn't any concurrency, blocking onNext() isn't possible.
     *
     * The other way to do this without blocking would be to use RxJava's backpressure subscriber which only requests
     * one item at a time. We do not have the backpressure version of rxjava as yet.
     */
    private static final class QueueSubject extends Subject<InvokerTask, InvokerTask> {

        private final LinkedBlockingQueue<Object> notifications;
        private final NotificationLite<Object> nl = NotificationLite.instance();

        private QueueSubject(final LinkedBlockingQueue<Object> notifications) {
            super(new OnSubscribeAction(notifications));
            this.notifications = notifications;
        }

        protected static QueueSubject create() {
            final LinkedBlockingQueue<Object> notifications = new LinkedBlockingQueue<>();
            return new QueueSubject(notifications);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onCompleted() {
            notifications.add(nl.completed());
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onError(Throwable e) {
            notifications.add(nl.error(e));
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(final InvokerTask task) {
            notifications.add(nl.next(task));
        }

        private static class OnSubscribeAction implements OnSubscribe<InvokerTask> {

            private final LinkedBlockingQueue<Object> notifications;
            private final NotificationLite<InvokerTask<?>> nl = NotificationLite.instance();

            public OnSubscribeAction(LinkedBlockingQueue<Object> notifications) {
                this.notifications = notifications;
            }

            @Override
            public void call(Subscriber<? super InvokerTask> subscriber) {
                boolean terminate = false;
                while (!terminate) {
                    try {
                        // TODO: handle InterruptedException here better
                        Object notification = notifications.take(); // Blocks when nothing available.
                        nl.accept(subscriber, notification);
                    } catch (InterruptedException e) {
                        Thread.interrupted(); // Reset the interrupted flag for the code upstream.
                        logger.error(
                                "Interrupted while waiting for next channel invocation task. Exiting the subscription",
                                e);
                        subscriber.onError(e);
                        terminate = true;
                    }
                }
            }
        }
    }

    private abstract class InvokerTask<T> {

        protected final Callable<Observable<T>> actual;

        private InvokerTask(Callable<Observable<T>> actual) {
            this.actual = actual;
        }

        protected abstract void execute();
    }

    private class InvokerTaskWithAck extends InvokerTask<Void> {

        private final Subscriber<? super Void> subscriberForThisTask;

        private InvokerTaskWithAck(Callable<Observable<Void>> actual, Subscriber<? super Void> subscriberForThisTask) {
            super(actual);
            this.subscriberForThisTask = subscriberForThisTask;
        }

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
                logger.error("Exception invoking the channel invocation task.", e);
                subscriberForThisTask.onError(e);
            }
        }
    }

    private class InvokerTaskWithResult<T> extends InvokerTask<T> {

        private final Subscriber<? super Observable<T>> subscriberForThisTask;

        private InvokerTaskWithResult(Callable<Observable<T>> actual, Subscriber<? super Observable<T>> subscriberForThisTask) {
            super(actual);
            this.subscriberForThisTask = subscriberForThisTask;
        }

        protected void execute() {
            try {
                subscriberForThisTask.onNext(actual.call());
                subscriberForThisTask.onCompleted();
            } catch (Throwable e) {
                logger.error("Exception invoking the channel invocation task.", e);
                subscriberForThisTask.onError(e);
            }
        }
    }
}

package com.netflix.eureka.client;

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.observers.SafeSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Processor class to serialize access to the underlying Interest Channel.
 *
 * @author David Liu
 */
public class InterestProcessor {
    protected final Queue<Task<Interest<InstanceInfo>>> taskQueue;
    protected final Scheduler.Worker taskProcessor;
    protected Observable<Void> interestStream;

    public InterestProcessor(final InterestChannel interestChannel) {
        this.taskQueue = new ConcurrentLinkedQueue<>();

        this.taskProcessor = Schedulers.computation().createWorker();
        taskProcessor.schedule(new Action0() {
            @Override
            public void call() {
                Task<Interest<InstanceInfo>> task = taskQueue.poll();
                if (task != null) {
                    Interest<InstanceInfo> interest = task.getInput();
                    if (interestStream != null) {
                        interestChannel.upgrade(interest).subscribe(task);
                    } else {
                        interestStream = interestChannel.register(interest).ignoreElements().cast(Void.class);
                        interestStream.subscribe(task);  // for propagation of error and complete if needed
                    }

                    // subscribe the tasks to the (singleton) channel stream to propagate onError and onComplete
                }

                taskProcessor.schedule(this, 100, TimeUnit.MILLISECONDS);  // repeat
            }
        });
    }

    public void shutdown() {
        taskProcessor.unsubscribe();
        taskQueue.clear();
    }

    public Observable<Void> forInterest(final Interest<InstanceInfo> interest) {
        final Task<Interest<InstanceInfo>> task = Task.newTask(interest);

        // create an observable that, when subscribed to, will initiate the offer of the task to the queue.
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                boolean result = taskQueue.offer(task);
                if (result) {
                    subscriber.onCompleted();
                } else {
                    subscriber.onError(new RuntimeException("Cannot initiate forInterest for interest: " + interest));
                }
            }
        }).mergeWith(task);
    }



    /**
     * This class is used to bridge the client's stream from the local registry with onError
     * and onComplete returns from single channel interest stream.
     *
     * A task instance is associated with each forInterest stream from client users.
     */
    protected static class Task<T> extends Subject<Void, Void> {

        private final SafeSubscriber<Void> safeSubscriber;
        private final T input;

        protected Task(T input, final ReplaySubject<Void> bridge) {
            super(new OnSubscribe<Void>() {
                @Override
                public void call(Subscriber<? super Void> subscriber) {
                    bridge.subscribe(subscriber);
                }
            });

            this.input = input;

            safeSubscriber = new SafeSubscriber<>(new Subscriber<Void>() {
                @Override
                public void onCompleted() {
                    bridge.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    bridge.onError(e);
                }

                @Override
                public void onNext(Void aVoid) {
                    bridge.onNext(aVoid);
                }
            });
        }

        public static <T> Task<T> newTask(T input) {
            ReplaySubject<Void> bridge = ReplaySubject.create();
            return new Task<>(input, bridge);
        }

        public T getInput() {
            return input;
        }

        @Override
        public void onCompleted() {
            safeSubscriber.onCompleted();
        }

        @Override
        public void onError(Throwable e) {
            safeSubscriber.onError(e);
        }

        @Override
        public void onNext(Void aVoid) {
            safeSubscriber.onCompleted();
        }
    }
}

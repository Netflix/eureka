package com.netflix.eureka.datastore;

import com.netflix.eureka.interests.ChangeNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;
import rx.observers.SafeSubscriber;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A special {@link Subject} implementation for {@link ChangeNotification}s. This has the capability to optionally
 * start/stop caching on demand, i.e. publish of data to the subscribers will be paused after calling the method
 * {@link #pause()} and the same can be resumed after calling the method {@link #resume()}
 *
 * @author Nitesh Kant
 */
public class NotificationsSubject<T> extends Subject<ChangeNotification<T>, ChangeNotification<T>>{

    public enum ResumeResult {NotPaused, DuplicateResume, Resumed}

    private enum ResumeState {NotPaused, Resuming, Error}

    private static final Logger logger = LoggerFactory.getLogger(NotificationsSubject.class);

    private final AtomicInteger resumeState = new AtomicInteger(ResumeState.NotPaused.ordinal());

    private AtomicBoolean paused;
    private final PublishSubject<ChangeNotification<T>> notificationSubject;
    private final NotificationsSubjectSubscriber subscriber;
    private final ConcurrentLinkedQueue<ChangeNotification<T>> notificationsWhenPaused; // TODO: See if this should be bounded.
    private volatile boolean completedWhenPaused;
    private volatile Throwable errorWhenPaused;

    protected NotificationsSubject(OnSubscribe<ChangeNotification<T>> onSubscribe,
                                   PublishSubject<ChangeNotification<T>> notificationSubject) {
        super(onSubscribe);
        subscriber = new NotificationsSubjectSubscriber();
        this.notificationSubject = notificationSubject;
        notificationsWhenPaused = new ConcurrentLinkedQueue<ChangeNotification<T>>();
        paused = new AtomicBoolean();
    }

    public static <T> NotificationsSubject<T> create() {
        final PublishSubject<ChangeNotification<T>> notificationSubject = PublishSubject.create();
        return new NotificationsSubject<T>(new OnSubscribe<ChangeNotification<T>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<T>> subscriber) {
                notificationSubject.subscribe(subscriber);
            }
        }, notificationSubject);
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void pause() {
        paused.set(true);
    }

    public ResumeResult resume() {
        if (isPaused()) {
            if (resumeState.compareAndSet(ResumeState.NotPaused.ordinal(), ResumeState.Resuming.ordinal())) {
                try {
                    ChangeNotification<T> nextPolled;
                    while ((nextPolled = notificationsWhenPaused.poll()) != null) { // drain pending queue.
                        notificationSubject.onNext(nextPolled); // Since pause flag is not yet unset, don't call this.onNext()
                    }

                    paused.set(false);

                    if (completedWhenPaused) {
                        onCompleted();
                    } else if (null != errorWhenPaused) {
                        onError(errorWhenPaused);
                    }

                    return ResumeResult.Resumed;
                } catch (Exception e) {
                    logger.error("Error while resuming notifications subject.", e);
                    resumeState.compareAndSet(ResumeState.Resuming.ordinal(), ResumeState.Error.ordinal());
                    onError(e);
                    return ResumeResult.Resumed;
                } finally {
                    resumeState.compareAndSet(ResumeState.Resuming.ordinal(), ResumeState.NotPaused.ordinal());
                }
            } else {
                return ResumeResult.DuplicateResume;
            }
        } else {
            return ResumeResult.NotPaused;
        }
    }

    @Override
    public void onCompleted() {
        subscriber.onCompleted();
    }

    @Override
    public void onError(Throwable e) {
        subscriber.onError(e);
    }

    @Override
    public void onNext(ChangeNotification<T> notification) {
        subscriber.onNext(notification);
    }

    /**
     * This makes sure that the {@link com.netflix.eureka.datastore.NotificationsSubject} follows Rx contracts i.e.
     * it does not honor onNext() after termination and onNext() error causes onError().
     */
    private class NotificationsSubjectSubscriber extends SafeSubscriber<ChangeNotification<T>> {

        public NotificationsSubjectSubscriber() {
            super(new Subscriber<ChangeNotification<T>>() {
                @Override
                public void onCompleted() {
                    if (paused.get()) {
                        completedWhenPaused = true;
                    } else {
                        notificationSubject.onCompleted();
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (paused.get()) {
                        errorWhenPaused = e;
                    } else {
                        notificationSubject.onError(e);
                    }
                }

                @Override
                public void onNext(ChangeNotification<T> notification) {
                    if (paused.get()) {
                        notificationsWhenPaused.add(notification);
                    } else {
                        notificationSubject.onNext(notification);
                    }
                }
            });
        }
    }
}

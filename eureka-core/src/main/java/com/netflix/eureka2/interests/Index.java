package com.netflix.eureka2.interests;

import com.netflix.eureka2.datastore.NotificationsSubject;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.Iterator;


/**
 * An index implementation associated with an {@link Interest}. <br/>
 * An index contains two primary sources of data:
 * <ul>
 <li>Initial data</li>
 <li>Real time data</li>
 </ul>
 *
 * The "real time data" is piped from the source of data for this index and the "initial data" is an optional set of
 * data that is sent to any {@link Subscriber} of this index before any {@link ChangeNotification} is sent.
 *
 * If the initial data source (implemented as {@link Index.InitStateHolder}) is empty, then it is assumed that the real
 * time data source replays all initial data which will be required for any subscriber of this interest to create the
 * complete view of the data.
 *
 * <h2>Why do we need two sources?</h2>
 *
 * Typically an index is used to create streams of data for a matching {@link Interest}. Since, an index here is only
 * publishing a {@link ChangeNotification} it is imperative that all the notifications from the start of time are sent
 * to the {@link Subscriber} so that it can create a full view of the data that it is interested in.
 *
 * Now, in order to relay all {@link ChangeNotification}'s to all subscribers, we need to maintain all these
 * notifications for the entire lifetime of a server. This can be quiet expensive if the original data-source changes
 * often.
 *
 * So, it seems quiet obvious that we need to compact these notifications when possible. eg: If an item is deleted, any
 * new subscriber do not need to know about it at all. So, this notification can be ignored by any new subscriber that
 * subscribed after the item is deleted.
 *
 * It is very important for the sanity of this data that all the {@link ChangeNotification} from a single source are
 * completely ordered. So, it is also obvious that we need to pass this data through a queue. However, the presence of
 * a queue creates an unnecessary queuing point for subscribers who have already got the initial state as the order is
 * maintained by the source.
 *
 * For this reason, we have two data sources, and any subscriber to this index receives notifications from these sources
 * in order. In all cases, all notifications from the initial data source is sent before the real time data is sent to
 * the subscriber.
 *
 * <h2>Is there any message loss between two sources?</h2>
 *
 * All {@link ChangeNotification}s received by this index which are essentially the notifications from the original
 * data source applicable to this index (i.e. {@link Interest#matches(Object)} returns {@code true}) are sent to both
 * these data sources (initial and real time).
 * At the start of a subscription the {@link Index.InitStateHolder} makes sure that the returned {@link Iterator} has
 * all data that is received till now and no more data is added to the real time source till this iterator is created.
 *
 * <h2>How does this guarantee ordering between sources?</h2>
 *
 * We cache all change notifications from the real time data source, till all the notifications from the initial data
 * source is sent to the subscriber. Hence, the change notifications from the real time data source never reaches the
 * subscriber till the init state is completed.
 *
 * @author Nitesh Kant
 */
public class Index<T> extends Subject<ChangeNotification<T>, ChangeNotification<T>> {

    private final Interest<T> interest;
    private final NotificationsSubject<T> notificationsSubject;

    protected Index(final Interest<T> interest, final InitStateHolder<T> initStateHolder,
                    final PublishSubject<ChangeNotification<T>> realTimeSource) {
        super(new OnSubscribe<ChangeNotification<T>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<T>> subscriber) {
                ConnectableObservable<ChangeNotification<T>> realTimePublish = realTimeSource.publish();
                realTimePublish.subscribe(subscriber); // For real time notifications.
                for (ChangeNotification<T> notification : initStateHolder) {
                    subscriber.onNext(notification);
                }
                realTimePublish.connect(); // This makes sure that there is complete order between init state & real time.
            }
        });
        this.interest = interest;
        this.notificationsSubject = initStateHolder.getNotificationSubject();
        this.notificationsSubject.subscribe(initStateHolder);// It is important to ALWAYS update init state first otherwise, we will lose data (see class javadoc)
        this.notificationsSubject.subscribe(realTimeSource);
    }

    public Interest getInterest() {
        return interest;
    }

    @Override
    public void onCompleted() {
        notificationsSubject.onCompleted();
    }

    @Override
    public void onError(Throwable e) {
        notificationsSubject.onError(e);
    }

    @Override
    public void onNext(ChangeNotification<T> notification) {
        notificationsSubject.onNext(notification);
    }

    public static <T> Index<T> forInterest(final Interest<T> interest,
                                           final Observable<ChangeNotification<T>> dataSource,
                                           final InitStateHolder<T> initStateHolder) {
        PublishSubject<ChangeNotification<T>> realTimeSource = PublishSubject.create();
        Index<T> toReturn = new Index<T>(interest, initStateHolder, realTimeSource);

        dataSource.filter(new Func1<ChangeNotification<T>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<T> notification) {
                return interest.matches(notification.getData());
            }
        }).subscribe(toReturn); // data source sends all notifications irrespective of the interest set. Here we filter based on interest.
        return toReturn;
    }

    /**
     * An initial data source for {@link com.netflix.eureka2.interests.Index}.
     *
     * <h2>Producer</h2>
     * There will always be a single producer (even if multiple the updates will be sequenced out of this context of
     * this holder) queue.
     *
     * The producer will always be the {@link com.netflix.eureka2.interests.Index}, the updates to which
     * (onNext/onError/onComplete) are always sequence i.e. it is not invoked concurrently.
     *
     * <h2>Consumers</h2>
     * There will be multiple consumers of this data i.e. multiple consumers can call {@link #iterator()} on this class.
     *
     * <h2>Consistency guarantees</h2>
     *
     * This implementation should always have a consistency between the data written by
     * {@link #onNext(ChangeNotification)} and what is returned in the {@link #iterator()}. There should not be any
     * missed notifications even if {@link #onNext(ChangeNotification)} and {@link #iterator()} are called concurrently.
     *
     * @param <T> Type of data that this holds.
     */
    protected static abstract class InitStateHolder<T> extends Subscriber<ChangeNotification<T>>
            implements Iterable<ChangeNotification<T>> {

        protected final Iterator<ChangeNotification<T>> EMPTY_ITERATOR = new Iterator<ChangeNotification<T>>() {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public ChangeNotification<T> next() {
                return null;
            }

            @Override
            public void remove() {
            }
        };

        private volatile boolean done;
        private final NotificationsSubject<T> notificationSubject;

        protected InitStateHolder(NotificationsSubject<T> notificationSubject) {
            this.notificationSubject = notificationSubject;
        }

        @Override
        public Iterator<ChangeNotification<T>> iterator() {

            if (isDone()) {
                return EMPTY_ITERATOR;
            }

            try {
                notificationSubject.pause();
                return _newIterator();
            } finally {
                notificationSubject.resume();
            }
        }

        @Override
        public final void onCompleted() {
            done = true;
            clearAllNotifications(); // Completion == shutdown, so after this, there isn't anything to be done.
        }

        @Override
        public final void onError(Throwable e) {
            done = true; // Since, any one interested in this source will also be interested in the real time source,
                         // we leave it to the real time source to propagate this error. We just return an empty iterator
                         // whenever the upstream source is done (i.e. onComplete/onError on this instance)
            clearAllNotifications(); // Completion == shutdown, so after this, there isn't anything to be done.
        }

        @Override
        public final void onNext(ChangeNotification<T> notification) {
            // Since we pause notifications during iterator creation, we will not get an onNext when iterator creation is in progress.
            addNotification(notification);
        }

        protected NotificationsSubject<T> getNotificationSubject() {
            return notificationSubject;
        }

        protected boolean isDone() {
            return done;
        }

        protected abstract void addNotification(ChangeNotification<T> notification);

        protected abstract void clearAllNotifications();

        protected abstract Iterator<ChangeNotification<T>> _newIterator();
    }
}

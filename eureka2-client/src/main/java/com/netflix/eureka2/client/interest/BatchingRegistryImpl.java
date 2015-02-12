package com.netflix.eureka2.client.interest;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.interests.StreamStateNotification.BufferingState;
import com.netflix.eureka2.utils.rx.PauseableSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class BatchingRegistryImpl<T> implements BatchingRegistry<T> {

    private static final Logger logger = LoggerFactory.getLogger(BatchingRegistryImpl.class);

    private final Map<Interest<T>, BufferingState> interestsBufferingState =
            new ConcurrentHashMap<Interest<T>, BufferingState>();

    private final PauseableSubject<Boolean> updatesSubject = PauseableSubject.create();

    /**
     * As channels may reconnect, and this object is scoped to an {@link com.netflix.eureka2.interests.IndexRegistry}
     * we need to handle multiple subscriptions from subsequent channels, with state cleanup.
     * If the change notification stream from channel terminates, clean the state, and start over from the
     * new channel.
     */
    private final PublishSubject<Observable<ChangeNotification<T>>> notificationSources = PublishSubject.create();

    public BatchingRegistryImpl() {
        notificationSources.switchMap(
                new Func1<Observable<ChangeNotification<T>>, Observable<Boolean>>() {
                    @Override
                    public Observable<Boolean> call(Observable<ChangeNotification<T>> notifications) {
                        return notifications.map(new Func1<ChangeNotification<T>, Boolean>() {
                            @Override
                            public Boolean call(ChangeNotification<T> notification) {
                                if (notification instanceof StreamStateNotification) {
                                    StreamStateNotification<T> stateNotification = (StreamStateNotification<T>) notification;
                                    interestsBufferingState.put(stateNotification.getInterest(), stateNotification.getBufferingState());
                                    return true;
                                }
                                return null;
                            }
                        }).filter(new Func1<Boolean, Boolean>() {
                            @Override
                            public Boolean call(Boolean value) {
                                return value != null;
                            }
                        }).materialize().flatMap(new Func1<Notification<Boolean>, Observable<Boolean>>() {
                            @Override
                            public Observable<Boolean> call(Notification<Boolean> materialized) {
                                switch (materialized.getKind()) {
                                    case OnNext:
                                        return Observable.just(materialized.getValue());
                                    case OnError:
                                        if (logger.isDebugEnabled()) {
                                            logger.debug("Swallowed observable error", materialized.getThrowable());
                                        }
                                }
                                // Set all to FinishBuffering to close opened buffered streams
                                for(Interest<T> i: interestsBufferingState.keySet()) {
                                    interestsBufferingState.put(i, BufferingState.FinishBuffering);
                                }
                                return Observable.just(false);
                            }
                        }).doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                interestsBufferingState.clear();
                            }
                        });
                    }
                }
        ).subscribe(updatesSubject);
    }

    @Override
    public void subscribe(Observable<ChangeNotification<T>> changeNotifications) {
        notificationSources.onNext(changeNotifications);
    }

    @Override
    public Observable<BufferingState> forInterest(final Interest<T> interest) {
        return Observable.create(new OnSubscribe<BufferingState>() {
            @Override
            public void call(Subscriber<? super BufferingState> subscriber) {
                updatesSubject.pause();
                try {
                    BufferingState current = shouldBatch(interest);
                    if (current != BufferingState.FinishBuffering) {
                        subscriber.onNext(current);
                    }
                    updatesSubject.map(new Func1<Boolean, BufferingState>() {
                        @Override
                        public BufferingState call(Boolean tick) {
                            return shouldBatch(interest);
                        }
                    }).subscribe(subscriber);
                } finally {
                    updatesSubject.resume();
                }
            }
        }).distinctUntilChanged();
    }

    @Override
    public void retainAll(Interest<T> interest) {
        Set<Interest<T>> toRemove;
        if (interest instanceof MultipleInterests) {
            toRemove = ((MultipleInterests<T>) interest).flatten();
        } else {
            toRemove = Collections.singleton(interest);
        }
        interestsBufferingState.keySet().removeAll(toRemove);
    }

    /**
     * Given atomic or complex interest, tell if we are in the batch mode. We should batch
     * if at least one of the atomic interests of the given interest is eligible to batching.
     * If all atomic streams are in unknown state (there was no state notification
     * for it received), the unknown batching state is returned.
     */
    @Override
    public BufferingState shouldBatch(Interest<T> interest) {
        if (interest instanceof MultipleInterests) {
            Set<Interest<T>> interests = ((MultipleInterests<T>) interest).getInterests();
            boolean allUnknown = true;
            for (Interest<T> atomic : interests) {
                BufferingState state = shouldBatchAtomic(atomic);
                if (state == BufferingState.Buffer) {
                    return BufferingState.Buffer;
                }
                allUnknown &= state == BufferingState.Unknown;
            }
            return allUnknown ? BufferingState.Unknown : BufferingState.FinishBuffering;
        }
        return shouldBatchAtomic(interest);
    }

    @Override
    public void shutdown() {
        updatesSubject.onCompleted();
    }

    private BufferingState shouldBatchAtomic(Interest<T> atomic) {
        BufferingState state = interestsBufferingState.get(atomic);
        if (state == null) {
            return BufferingState.Unknown;
        }
        if (state == BufferingState.Buffer) {
            return BufferingState.Buffer;
        }
        return BufferingState.FinishBuffering;
    }
}

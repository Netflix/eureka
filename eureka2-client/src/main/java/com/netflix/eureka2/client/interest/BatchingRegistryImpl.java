package com.netflix.eureka2.client.interest;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
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

    private final Map<Interest<T>, BufferState> interestsBufferingState =
            new ConcurrentHashMap<Interest<T>, BufferState>();

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
                        return notifications
                                .filter(ChangeNotifications.streamStateFilter())
                                .map(new Func1<ChangeNotification<T>, Boolean>() {
                                    @Override
                                    public Boolean call(ChangeNotification<T> notification) {
                                        StreamStateNotification<T> stateNotification = (StreamStateNotification<T>) notification;
                                        interestsBufferingState.put(stateNotification.getInterest(), stateNotification.getBufferState());
                                        return true;
                                    }
                                }).materialize().map(new Func1<Notification<Boolean>, Boolean>() {
                                    @Override
                                    public Boolean call(Notification<Boolean> materialized) {
                                        switch (materialized.getKind()) {
                                            case OnNext:
                                                return materialized.getValue();
                                            case OnCompleted:
                                                break; // No-op
                                            case OnError:
                                                if (logger.isDebugEnabled()) {
                                                    logger.debug("Swallowed observable error", materialized.getThrowable());
                                                }
                                                break;
                                        }
                                        return false;
                                    }
                                }).doOnTerminate(new Action0() {
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
    public void connectTo(Observable<ChangeNotification<T>> changeNotifications) {
        notificationSources.onNext(changeNotifications);
    }

    @Override
    public Observable<BufferState> forInterest(final Interest<T> interest) {
        return Observable.create(new OnSubscribe<BufferState>() {
            @Override
            public void call(Subscriber<? super BufferState> subscriber) {
                updatesSubject.pause();
                try {
                    BufferState current = shouldBatch(interest);
                    subscriber.onNext(current);
                    updatesSubject.map(new Func1<Boolean, BufferState>() {
                        @Override
                        public BufferState call(Boolean tick) {
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
        Set<Interest<T>> toKeep;
        if (interest instanceof MultipleInterests) {
            toKeep = ((MultipleInterests<T>) interest).flatten();
        } else {
            toKeep = Collections.singleton(interest);
        }
        interestsBufferingState.keySet().retainAll(toKeep);
    }

    /**
     * Given atomic or complex interest, tell if we are in the batch mode. We should batch
     * if at least one of the atomic interests of the given interest is eligible to batching.
     * If all atomic streams are in unknown state (there was no state notification
     * for it received), the unknown batching state is returned.
     */
    @Override
    public BufferState shouldBatch(Interest<T> interest) {
        if (interest instanceof MultipleInterests) {
            Set<Interest<T>> interests = ((MultipleInterests<T>) interest).getInterests();
            boolean allUnknown = true;
            for (Interest<T> atomic : interests) {
                BufferState state = shouldBatchAtomic(atomic);
                if (state == BufferState.BufferStart) {
                    return BufferState.BufferStart;
                }
                allUnknown &= state == BufferState.Unknown;
            }
            return allUnknown ? BufferState.Unknown : BufferState.BufferEnd;
        }
        return shouldBatchAtomic(interest);
    }

    @Override
    public void shutdown() {
        updatesSubject.onCompleted();
        notificationSources.onCompleted();
    }

    private BufferState shouldBatchAtomic(Interest<T> atomic) {
        BufferState state = interestsBufferingState.get(atomic);
        if (state == null || state == BufferState.Unknown) {
            return BufferState.Unknown;
        }
        return state;
    }
}

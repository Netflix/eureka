package com.netflix.eureka2.client.interest;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.functions.Func2;

/**
 * This class collects information about batching statuses of individual atomic interests.
 *
 * @author Tomasz Bak
 */
public class BatchingTracker {

    public enum BatchingState {Unknown, Batching, FinishBatching}

    private final Map<Interest<InstanceInfo>, BatchingState> interestsBatchingState =
            new ConcurrentHashMap<Interest<InstanceInfo>, BatchingState>();

    public void markBatchingFor(Interest<InstanceInfo> interest) {
        interestsBatchingState.put(interest, BatchingState.Batching);
    }

    public void unmarkBatchingFor(Interest<InstanceInfo> interest) {
        interestsBatchingState.put(interest, BatchingState.FinishBatching);
    }

    /**
     * Given atomic or complex interest, tell if we are in the batch mode. We should batch
     * if at least one of the atomic interests of the given interest is eligible to batching.
     * If one or more atomic streams are in unknown state (there was no state notification
     * for it received), the unknown batching state is returned.
     */
    public BatchingState shouldBatch(Interest<InstanceInfo> interest) {
        if (interest instanceof MultipleInterests) {
            Set<Interest<InstanceInfo>> interests = ((MultipleInterests<InstanceInfo>) interest).getInterests();
            for (Interest<InstanceInfo> atomic : interests) {
                BatchingState state = shouldBatchAtomic(atomic);
                if (state != BatchingState.FinishBatching) {
                    return state;
                }
            }
            return BatchingState.FinishBatching;
        }
        return shouldBatchAtomic(interest);
    }

    private BatchingState shouldBatchAtomic(Interest<InstanceInfo> atomic) {
        BatchingState state = interestsBatchingState.get(atomic);
        if (state == null) {
            return BatchingState.Unknown;
        }
        if (state == BatchingState.Batching) {
            return BatchingState.Batching;
        }
        return BatchingState.FinishBatching;
    }

    /**
     * Remove all atomic interests belonging to the given interest from the internal state.
     */
    public void removeInterest(Interest<InstanceInfo> interest) {
        if (interest instanceof MultipleInterests) {
            for (Interest<InstanceInfo> atomic : ((MultipleInterests<InstanceInfo>) interest).flatten()) {
                interestsBatchingState.remove(atomic);
            }
        } else {
            interestsBatchingState.remove(interest);
        }
    }

    /**
     * This function is relevant for long lived {@link BatchingTracker}, that is associated with the channel.
     * We need to remove all interests, that are no longer subscribed to.
     */
    public void subscribeForCleanup(Observable<Interest<InstanceInfo>> interestChangeStream) {
        interestChangeStream.subscribe(new Subscriber<Interest<InstanceInfo>>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Interest<InstanceInfo> interest) {
                removeInterest(interest);
            }
        });
    }

    /**
     * Return a function that when applied to observable of {@link ChangeNotification}, updates {@link BatchingTracker}
     * state and issues a value each time the state has changed. The state values are not relevant as server only
     * as a trigger.
     */
    public Func1<ChangeNotification<InstanceInfo>, Boolean> markerUpdateFun() {
        return new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> notification) {
                if (notification instanceof StreamStateNotification) {
                    StreamStateNotification<InstanceInfo> stateNotification = (StreamStateNotification<InstanceInfo>) notification;
                    switch (stateNotification.getKind()) {
                        case Buffer:
                            markBatchingFor(stateNotification.getInterest());
                            break;
                        case FinishBuffering:
                            unmarkBatchingFor(stateNotification.getInterest());
                            break;
                    }
                    return true;
                }
                return null;
            }
        };
    }

    /**
     * A function that when applied to an observable stream, evaluates batching state for the given
     * clientInterest interest set. The corresponding {@link BatchingTracker} provides information about
     * state of each atomic interest.
     */
    public <T> Func1<T, BatchingState> batchingFun(final Interest<InstanceInfo> clientInterest) {
        return new Func1<T, BatchingState>() {
            @Override
            public BatchingState call(T value) {
                return shouldBatch(clientInterest);
            }
        };
    }

    /**
     * Given two batching hint sources (one from registry and one from the channel), combine them into a final hint.
     * If any of those indicates batching state, the result is true.
     */
    public static Observable<ChangeNotification<InstanceInfo>> combine(Observable<BatchingState> registryBatching, Observable<BatchingState> channelBatching) {
        return Observable.combineLatest(registryBatching, channelBatching, new Func2<BatchingState, BatchingState, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(BatchingState batchFirst, BatchingState bathSecond) {
                if (batchFirst == BatchingState.Batching || bathSecond == BatchingState.Batching) {
                    return new ChangeNotification<InstanceInfo>(Kind.Buffer, null);
                }
                return new ChangeNotification<InstanceInfo>(Kind.FinishBuffering, null);
            }
        }).distinct();
    }
}

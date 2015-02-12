package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.interests.StreamStateNotification.BufferingState;
import rx.Observable;
import rx.functions.Func2;

import static com.netflix.eureka2.utils.rx.RxFunctions.filterNullValuesFunc;

/**
 * @author Tomasz Bak
 */
public final class BatchFunctions {

    private BatchFunctions() {
    }

    /**
     * Given two batching hint sources (one from registry and one from the channel), combine them into a final hint.
     * If any of those indicates batching state, the result is true.
     */
    public static Observable<Boolean> combine(Observable<BufferingState> registryBatching, Observable<BufferingState> channelBatching) {
        return Observable.combineLatest(registryBatching, channelBatching, new Func2<BufferingState, BufferingState, Boolean>() {
            @Override
            public Boolean call(BufferingState batchFirst, BufferingState batchSecond) {
                if (batchFirst == BufferingState.Unknown && batchSecond == BufferingState.Unknown) {
                    return null;
                }
                return batchFirst == BufferingState.Buffer || batchSecond == BufferingState.Buffer;
            }
        }).filter(filterNullValuesFunc());
    }
}

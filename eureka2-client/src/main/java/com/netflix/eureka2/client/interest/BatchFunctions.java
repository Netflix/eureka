package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.interests.StreamStateNotification.BufferState;
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
    public static Observable<Boolean> combine(Observable<BufferState> registryBatching, Observable<BufferState> channelBatching) {
        return Observable.combineLatest(registryBatching, channelBatching, new Func2<BufferState, BufferState, Boolean>() {
            @Override
            public Boolean call(BufferState batchFirst, BufferState batchSecond) {
                if (batchFirst == BufferState.Unknown && batchSecond == BufferState.Unknown) {
                    return null;
                }
                return batchFirst == BufferState.BufferStart || batchSecond == BufferState.BufferStart;
            }
        }).filter(filterNullValuesFunc());
    }
}

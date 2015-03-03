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
     * <p>
     * The following rules are applied:
     * <ul>
     *     <li>
     *         (BufferStart, *) | (*, BufferStart) - set batching to true, whenever there is at least one batch open
     *                                               (either from channel or the registry),
     *     </li>
     *     <li>
     *         (Unknown, BufferEnd|Unknown) | (BufferEnd|Unknown, Unknown) - do not emit anything.
     *             We cannot propagate BufferEnd marker, until we know what comes from the unknown source.
     *     </li>
     *     <li>
     *         (BufferEnd|BufferEnd) - set batching to false, if both sources report this state
     *     </li>
     * </ul>
     */
    public static Observable<Boolean> combine(Observable<BufferState> registryBatching, Observable<BufferState> channelBatching) {
        return Observable.combineLatest(registryBatching, channelBatching, new Func2<BufferState, BufferState, Boolean>() {
            @Override
            public Boolean call(BufferState batchFirst, BufferState batchSecond) {
                if (batchFirst == BufferState.BufferStart || batchSecond == BufferState.BufferStart) {
                    return true;
                }
                if (batchFirst == BufferState.Unknown || batchSecond == BufferState.Unknown) {
                    return null;
                }
                return false; // BufferState.BufferEnd
            }
        }).filter(filterNullValuesFunc());
    }
}

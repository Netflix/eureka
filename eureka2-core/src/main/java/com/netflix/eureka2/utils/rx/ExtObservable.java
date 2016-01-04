/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.utils.rx;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.functions.Func1;

/**
 */
public class ExtObservable {

    private static final Observable<?> ON_COMPLETE_MARKER = Observable.error(new OnCompleteMarkerException());

    /**
     * Standard merge operator completes the stream only when all internal observables complete or there is an error.
     * In some scenarios we want to complete immediately, when any of the internal observables completes. This
     * method provides this functionality.
     */
    public static <T> Observable<T> mergeWhenAllActive(Observable<T>... observables) {
        List<Observable<T>> wrapped = new ArrayList<>(observables.length);
        for (int i = 0; i < observables.length; i++) {
            wrapped.add(observables[i].concatWith((Observable<T>) ON_COMPLETE_MARKER));
        }
        return Observable.merge(wrapped).onErrorResumeNext(new Func1<Throwable, Observable<? extends T>>() {
            @Override
            public Observable<? extends T> call(Throwable e) {
                if (e instanceof OnCompleteMarkerException) {
                    return Observable.empty();
                }
                return Observable.error(e);
            }
        });
    }

    private static class OnCompleteMarkerException extends Exception {
    }
}

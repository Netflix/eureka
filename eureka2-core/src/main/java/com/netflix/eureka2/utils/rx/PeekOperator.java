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

import rx.Observable;
import rx.functions.Func2;
import rx.subjects.PublishSubject;

/**
 * Operator that allows to peek at first item in the stream, and subscribing to an {@link Observable} as if
 * the first item was still there.
 */
public class PeekOperator<I, O> implements Observable.Transformer<I, O> {

    public interface PeekHandler<I, O> extends Func2<I, Observable<I>, Observable<O>> {
    }

    private final PeekHandler<I, O> handler;

    public PeekOperator(PeekHandler<I, O> handler) {
        this.handler = handler;
    }

    @Override
    public Observable<O> call(Observable<I> observable) {
        return Observable.create(subscriber -> {
            PeekSession peekSession = new PeekSession();
            observable
                    .doOnCompleted(() -> peekSession.onCompleted())
                    .flatMap(next -> {
                        return peekSession.onNext(next);
                    })
                    .doOnError(e -> peekSession.onError(e))
                    .subscribe(subscriber);
        });
    }

    public static <I, O> Observable.Transformer<I, O> peek(PeekHandler<I, O> handler) {
        return new PeekOperator<>(handler);
    }

    private class PeekSession {

        private PublishSubject<I> input;

        public Observable<O> onNext(I next) {
            if (input == null) {
                input = PublishSubject.create();
                return handler.call(next, Observable.just(next).concatWith(input));
            }
            input.onNext(next);
            return Observable.empty();
        }

        public void onError(Throwable e) {
            if (input != null) {
                input.onError(e);
            }
        }

        public void onCompleted() {
            if (input != null) {
                input.onCompleted();
            }
        }
    }
}

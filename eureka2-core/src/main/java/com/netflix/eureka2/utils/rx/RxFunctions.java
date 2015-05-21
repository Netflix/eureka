package com.netflix.eureka2.utils.rx;

import java.util.List;

import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public final class RxFunctions {

    private static final Func1<Object, Boolean> FILTER_NULL_VALUES_FUNC = new Func1<Object, Boolean>() {
        @Override
        public Boolean call(Object o) {
            return o != null;
        }
    };

    private static final Func1<Notification<?>, Notification<?>> SWALLOW_ERROR_FUNC = new Func1<Notification<?>, Notification<?>>() {
        @Override
        public Notification<?> call(Notification<?> notification) {
            return notification.getKind() == Kind.OnError ? Notification.createOnCompleted() : notification;
        }
    };

    private RxFunctions() {
    }

    public static <T> Func1<T, Boolean> filterNullValuesFunc() {
        return (Func1<T, Boolean>) FILTER_NULL_VALUES_FUNC;
    }

    /**
     * Convert onError to onCompleted. This is useful when merging multiple observables, where onError
     * would interrupt the whole stream.
     */
    public static <T> Transformer<T, T> swallowError() {
        return new Transformer<T, T>() {
            @Override
            public Observable<T> call(Observable<T> observable) {
                return observable.materialize().map(SWALLOW_ERROR_FUNC).dematerialize();
            }
        };
    }

    /**
     * Return a list of latest observed values. For example for windows size 3, and stream {A, B, C, D}
     * this would emit {A}, {A,B}, {A,B,C}, {B,C,D}.
     */
    public static <T> Transformer<T, List<T>> lastWindow(int count) {
        return new Transformer<T, List<T>>() {
            @Override
            public Observable<List<T>> call(Observable<T> observable) {
                return null;
            }
        };
    }
}

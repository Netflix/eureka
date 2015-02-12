package com.netflix.eureka2.utils.rx;

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

    private RxFunctions() {
    }

    public static <T> Func1<T, Boolean> filterNullValuesFunc() {
        return (Func1<T, Boolean>) FILTER_NULL_VALUES_FUNC;
    }
}

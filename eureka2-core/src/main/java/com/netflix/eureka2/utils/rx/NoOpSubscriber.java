package com.netflix.eureka2.utils.rx;

import rx.Subscriber;

public class NoOpSubscriber<T> extends Subscriber<T> {
    @Override
    public void onCompleted() {

    }

    @Override
    public void onError(Throwable e) {

    }

    @Override
    public void onNext(T t) {

    }
}

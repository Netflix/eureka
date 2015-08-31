package com.netflix.eureka2.utils.rx;

import org.slf4j.Logger;
import rx.Subscriber;

/**
 * @author David Liu
 */
public class LoggingSubscriber<T> extends Subscriber<T> {
    private final Logger loggerToUse;

    public LoggingSubscriber(Logger loggerToUse) {
        this.loggerToUse = loggerToUse;
    }

    @Override
    public void onCompleted() {
        loggerToUse.info("subscriber onCompleted");
    }

    @Override
    public void onError(Throwable e) {
        loggerToUse.warn("subscriber onErrored", e);
    }

    @Override
    public void onNext(T t) {
        loggerToUse.info("subscriber onNext: {}", t);
    }
}

package com.netflix.eureka2.utils.rx;

import org.slf4j.Logger;
import rx.Subscriber;

/**
 * @author David Liu
 */
public class LoggingSubscriber<T> extends Subscriber<T> {
    private final Logger loggerToUse;
    private final String prefix;

    public LoggingSubscriber(Logger loggerToUse) {
        this(loggerToUse, "");
    }

    public LoggingSubscriber(Logger loggerToUse, String prefix) {
        this.loggerToUse = loggerToUse;
        this.prefix = prefix;
    }

    @Override
    public void onCompleted() {
        loggerToUse.info("[{}] subscriber onCompleted", prefix);
    }

    @Override
    public void onError(Throwable e) {
        loggerToUse.warn("[{}] subscriber onErrored", prefix, e);
    }

    @Override
    public void onNext(T t) {
        loggerToUse.info("[{}] subscriber onNext: {}", prefix, t);
    }
}

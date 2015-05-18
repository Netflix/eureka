package com.netflix.eureka2;

import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface EurekaCloseable {

    Observable<Void> shutdown();

    Observable<Void> shutdown(Throwable cause);
}

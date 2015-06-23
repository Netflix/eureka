package com.netflix.eureka2.testkit.netrouter.internal;

import rx.Observable;

/**
 * @author Tomasz Bak
 */
public abstract class NetworkEvent {

    public abstract void acknowledge();

    public abstract Observable<Void> processed();
}

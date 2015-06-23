package com.netflix.eureka2.testkit.netrouter.internal;

import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import rx.Observable;
import rx.subjects.AsyncSubject;

/**
 * @author Tomasz Bak
 */
public class LinkEvent extends NetworkEvent {

    private final AsyncSubject<Void> processingStatus = AsyncSubject.create();

    public LinkEvent(NetworkLink networkLink) {
    }

    @Override
    public void acknowledge() {
        processingStatus.onCompleted();
    }

    @Override
    public Observable<Void> processed() {
        return processingStatus;
    }
}

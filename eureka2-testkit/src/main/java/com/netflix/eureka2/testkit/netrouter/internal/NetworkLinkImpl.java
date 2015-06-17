package com.netflix.eureka2.testkit.netrouter.internal;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * @author Tomasz Bak
 */
public class NetworkLinkImpl implements NetworkLink {

    private final AtomicBoolean isConnected = new AtomicBoolean(true);

    private final Subject<LinkEvent, LinkEvent> linkEventSubject = new SerializedSubject<>(PublishSubject.<LinkEvent>create());

    public Observable<LinkEvent> linkEvents() {
        return linkEventSubject;
    }

    @Override
    public boolean isUp() {
        return isConnected.get();
    }

    @Override
    public boolean connect() {
        boolean hasChanged = isConnected.compareAndSet(false, true);
        if (hasChanged) {
            linkEventSubject.onNext(new LinkEvent(this));
        }
        return hasChanged;
    }

    @Override
    public boolean disconnect() {
        boolean hasChanged = isConnected.compareAndSet(true, false);
        if (hasChanged) {
            linkEventSubject.onNext(new LinkEvent(this));
        }
        return hasChanged;
    }

    @Override
    public void limitBandwidthTo(int throughput, BandwidthUnit bandwidthUnit) {
        throw new IllegalStateException("not implemented yet");
    }

    @Override
    public boolean openUnlimitedBandwidth() {
        throw new IllegalStateException("not implemented yet");
    }

    @Override
    public void injectLatency(long time, TimeUnit timeUnit) {
        throw new IllegalStateException("not implemented yet");
    }

    @Override
    public void injectLatency(long time, long jitterAmplitude, TimeUnit timeUnit) {
        throw new IllegalStateException("not implemented yet");
    }
}

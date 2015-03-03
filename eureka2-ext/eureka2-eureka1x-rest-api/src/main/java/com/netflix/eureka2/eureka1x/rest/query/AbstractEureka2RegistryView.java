package com.netflix.eureka2.eureka1x.rest.query;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEureka2RegistryView<T> {

    private final Observable<ChangeNotification<InstanceInfo>> notifications;
    private final long refreshIntervalMs;
    private final Scheduler scheduler;

    private final BehaviorSubject<T> latestCopySubject = BehaviorSubject.create();
    private Subscription subscription;

    protected AbstractEureka2RegistryView(Observable<ChangeNotification<InstanceInfo>> notifications,
                                          long refreshIntervalMs,
                                          Scheduler scheduler) {
        this.notifications = notifications;
        this.refreshIntervalMs = refreshIntervalMs;
        this.scheduler = scheduler;
    }

    /**
     * Connect happens immediately after child object is constructed.
     */
    protected void connect() {
        subscription = notifications
                .compose(ChangeNotifications.<InstanceInfo>delineatedBuffers())
                .compose(ChangeNotifications.<InstanceInfo>snapshots())
                .throttleFirst(refreshIntervalMs, TimeUnit.MILLISECONDS, scheduler)
                .map(new Func1<Set<InstanceInfo>, T>() {
                    @Override
                    public T call(Set<InstanceInfo> instanceInfos) {
                        return updateSnapshot(instanceInfos);
                    }
                })
                .subscribe(latestCopySubject);
    }

    public void close() {
        subscription.unsubscribe();
    }

    public Observable<T> latestCopy() {
        return latestCopySubject;
    }

    protected abstract T updateSnapshot(Set<InstanceInfo> latestSnapshot);
}

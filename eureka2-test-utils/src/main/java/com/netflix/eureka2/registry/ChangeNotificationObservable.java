package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * A test observable that can be connected to registries via connect(source, RegistryObservable) and exposes
 * register/unregister methods.
 *
 * @author David Liu
 */
public class ChangeNotificationObservable extends Observable<ChangeNotification<InstanceInfo>> {
    private static final Logger logger = LoggerFactory.getLogger(ChangeNotificationObservable.class);

    private final Subject<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>> internalSubject;

    public static ChangeNotificationObservable create() {
        return create(PublishSubject.<ChangeNotification<InstanceInfo>>create());
    }

    public static ChangeNotificationObservable create(final Subject<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>> subject) {
        return new ChangeNotificationObservable(new OnSubscribe<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<InstanceInfo>> subscriber) {
                subject.subscribe(subscriber);
            }
        }, subject);
    }

    protected ChangeNotificationObservable(
            OnSubscribe<ChangeNotification<InstanceInfo>> f,
            Subject<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>> internalSubject
    ) {
        super(f);
        this.internalSubject = new SerializedSubject<>(internalSubject);
    }

    public void register(InstanceInfo instanceInfo) {
        internalSubject.onNext(new ChangeNotification<>(ChangeNotification.Kind.Add, instanceInfo));
    }

    public void unregister(String id) {
        InstanceInfo idOnlyInstanceInfo = new InstanceInfo.Builder()
                .withId(id)
                .build();
        unregister(idOnlyInstanceInfo);
    }

    public void unregister(InstanceInfo instanceInfo) {
        internalSubject.onNext(new ChangeNotification<>(ChangeNotification.Kind.Delete, instanceInfo));
    }

    public void onCompleted() {
        internalSubject.onCompleted();
    }

    public void onError(Throwable e) {
        internalSubject.onError(e);
    }

    public void onNext(ChangeNotification<InstanceInfo> notification) {
        internalSubject.onNext(notification);
    }

}

package com.netflix.eureka.registry;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.IndexRegistry;
import com.netflix.eureka.interests.InstanceInfoInitStateHolder;
import com.netflix.eureka.interests.Interest;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.PublishSubject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Nitesh Kant
 */
public class EurekaRegistryImpl implements EurekaRegistry {

    // TODO: Event notifications and registry.iterator() should be consistent.
    private final ConcurrentHashMap<String, ChangeNotification<InstanceInfo>> registry;
    private final IndexRegistry<InstanceInfo> indexRegistry;
    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject;

    public EurekaRegistryImpl() {
        registry = new ConcurrentHashMap<String, ChangeNotification<InstanceInfo>>();
        indexRegistry = new IndexRegistry<InstanceInfo>();
        notificationSubject = PublishSubject.create();
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        final String instanceId = instanceInfo.getId();
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                try {
                    ChangeNotification<InstanceInfo> addNotification =
                            new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instanceInfo);
                    registry.put(instanceId, addNotification);
                    notificationSubject.onNext(addNotification);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        final ChangeNotification<InstanceInfo> remove = registry.remove(instanceId);
        if (null != remove) {
            notificationSubject.onNext(new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete,
                                                                            remove.getData()));
        }
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                if (null == remove) {
                    subscriber.onError(new InstanceNotRegisteredException(instanceId));
                } else {
                    subscriber.onCompleted();
                }
            }
        });
    }

    @Override
    public Observable<Void> update(final InstanceInfo instanceInfo) {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                ChangeNotification<InstanceInfo> addNotification =
                        new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instanceInfo);
                ChangeNotification<InstanceInfo> existing = registry.put(instanceInfo.getId(), addNotification);
                try {
                    if (null == existing) {
                        notificationSubject.onNext(addNotification);
                    } else {
                        notificationSubject.onNext(new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Modify,
                                                                                        instanceInfo));
                    }
                } finally {
                    subscriber.onCompleted();
                }
            }
        });
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return indexRegistry.forInterest(interest, notificationSubject,
                                         new InstanceInfoInitStateHolder(registry.values()));
    }
}

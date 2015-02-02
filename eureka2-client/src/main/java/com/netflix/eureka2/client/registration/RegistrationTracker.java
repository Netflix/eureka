package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.BehaviorSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TODO deprecate me
 *
 * This is a bridging class that bridges the registration behaviour of the current eurekaClient (individual register()
 * and unregister() calls) to the new behaviour in the handler (a stream of instanceInfos). Going forward, this class
 * should go away as we change over the client api.
 *
 * @author David Liu
 */
public class RegistrationTracker {

    private final ConcurrentMap<String, RegistrationBridge> instanceIdVsRegistration;
    private final RegistrationHandler handler;

    public RegistrationTracker(RegistrationHandler handler) {
        this.handler = handler;
        this.instanceIdVsRegistration = new ConcurrentHashMap<>();
    }

    public Observable<Void> register(InstanceInfo instanceInfo) {
        final RegistrationBridge newRegistration = new RegistrationBridge(BehaviorSubject.<InstanceInfo>create());
        final RegistrationBridge existing = instanceIdVsRegistration.putIfAbsent(instanceInfo.getId(), newRegistration);

        if (null != existing) {
            return doRegister(instanceInfo, existing);
        } else {
            return doRegister(instanceInfo, newRegistration);
        }
    }

    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        final RegistrationBridge existing = instanceIdVsRegistration.remove(instanceInfo.getId());

        if (null != existing) {
            return doUnregister(existing);
        } else {
            return Observable.empty();
        }
    }

    private Observable<Void> doRegister(final InstanceInfo instanceInfo, final RegistrationBridge subject) {
        final AtomicBoolean firstTime = new AtomicBoolean(false);
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                if (firstTime.compareAndSet(false, true)) {
                    subject.register(instanceInfo);
                }
                subscriber.onCompleted();
            }
        });
    }

    private Observable<Void> doUnregister(final RegistrationBridge subject) {
        final AtomicBoolean firstTime = new AtomicBoolean(false);
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                if (firstTime.compareAndSet(false, true)) {
                    subject.unregister();
                }
                subscriber.onCompleted();
            }
        });
    }


    class RegistrationBridge {

        private final Subject<InstanceInfo, InstanceInfo> subject;
        private final RegistrationResponse response;
        private final Subscriber<Void> subscriber;

        protected RegistrationBridge(BehaviorSubject<InstanceInfo> subject) {
            this.subject = new SerializedSubject<>(subject);
            this.response = handler.register(subject.asObservable());
            this.subscriber = new NoOpSubscriber<>();

            // it's fine to eager subscribe here, the actual channel creation is not triggered until the first onNext
            response.subscribe(subscriber);
        }

        public void register(InstanceInfo instanceInfo) {
            subject.onNext(instanceInfo);
        }

        public void unregister() {
            subscriber.unsubscribe();
        }

        public RegistrationResponse getResponse() {
            return response;
        }
    }
}

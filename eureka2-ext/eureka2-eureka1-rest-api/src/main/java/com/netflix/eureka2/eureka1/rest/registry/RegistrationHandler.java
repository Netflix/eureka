package com.netflix.eureka2.eureka1.rest.registry;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka2xInstanceInfo;

/**
 * @author Tomasz Bak
 */
class RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationHandler.class);

    private final EurekaRegistrationClient registrationClient;
    private final Scheduler scheduler;
    private final PublishSubject<InstanceInfo> registrationSubject;

    private Subscription subscription;

    private volatile com.netflix.appinfo.InstanceInfo v1InstanceInfo; // volatile as we look at it from registration subscription
    private volatile long expiryTime;

    RegistrationHandler(EurekaRegistrationClient registrationClient, Scheduler scheduler) {
        this.registrationClient = registrationClient;
        this.scheduler = scheduler;
        this.registrationSubject = PublishSubject.create();
    }

    private void connect() {
        if (subscription == null) {
            subscription = registrationClient.register(registrationSubject)
                    .ignoreElements()
                    .cast(Void.class)
                    .doOnUnsubscribe(() -> registrationSubject.onCompleted())
                    .subscribe(new Subscriber<Void>() {
                        @Override
                        public void onCompleted() {
                            if (v1InstanceInfo != null) {
                                logger.info("Closing registration channel for instance {}", v1InstanceInfo.getId());
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (v1InstanceInfo != null) {
                                logger.error("Error in registration channel for instance " + v1InstanceInfo.getId(), e);
                            }
                        }

                        @Override
                        public void onNext(Void aVoid) {
                        }
                    });
        }
    }

    public void register(com.netflix.appinfo.InstanceInfo v1InstanceInfo) {
        connect();

        this.v1InstanceInfo = v1InstanceInfo;
        renew();

        InstanceInfo v2InstanceInfo = toEureka2xInstanceInfo(v1InstanceInfo);
        registrationSubject.onNext(v2InstanceInfo);
    }

    public void unregister() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    public void renew() {
        if (v1InstanceInfo != null) {
            this.expiryTime = scheduler.now() + v1InstanceInfo.getLeaseInfo().getDurationInSecs() * 1000;
        }
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public com.netflix.appinfo.InstanceInfo getV1InstanceInfo() {
        return v1InstanceInfo;
    }
}

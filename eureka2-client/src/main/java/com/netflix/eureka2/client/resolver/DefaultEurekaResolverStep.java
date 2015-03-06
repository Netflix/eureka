package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author David Liu
 */
class DefaultEurekaResolverStep implements EurekaRemoteResolverStep {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEurekaResolverStep.class);

    private final EurekaInterestClientBuilder interestClientBuilder;

    DefaultEurekaResolverStep(ServerResolver bootstrapResolver) {
        this(new EurekaInterestClientBuilder().withServerResolver(bootstrapResolver));
    }

    /* for testing */ DefaultEurekaResolverStep(EurekaInterestClientBuilder interestClientBuilder) {
        this.interestClientBuilder = interestClientBuilder;
    }

    @Override
    public ServerResolver forApps(String... appNames) {
        return forInterest(Interests.forApplications(appNames));
    }

    @Override
    public ServerResolver forVips(String... vipAddresses) {
        return forInterest(Interests.forVips(vipAddresses));
    }

    @Override
    public ServerResolver forInterest(final Interest<InstanceInfo> interest) {
        final AtomicReference<EurekaInterestClient> interestClientRef = new AtomicReference<>();
        Observable<ChangeNotification<InstanceInfo>> instanceInfoSource = Observable
                .create(new Observable.OnSubscribe<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(Subscriber<? super ChangeNotification<InstanceInfo>> subscriber) {
                        logger.info("Starting temporary interestClient for eureka resolver");
                        EurekaInterestClient interestClient = interestClientBuilder.build();
                        interestClientRef.set(interestClient);
                        interestClient.forInterest(interest).subscribe(subscriber);
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        EurekaInterestClient interestClient = interestClientRef.getAndSet(null);
                        if (interestClient != null) {
                            interestClient.shutdown();
                            logger.info("Shutting down temporary interestClient for eureka resolver");
                        }
                    }
                });

        return new EurekaServerResolver(instanceInfoSource);
    }
}

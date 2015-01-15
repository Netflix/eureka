package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.channel.InterestChannelImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.interests.SourcedModifyNotification;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author David Liu
 */
@Singleton
public class InterestHandlerImpl implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(InterestChannelImpl.class);

    private final AtomicBoolean isShutdown;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final ClientInterestChannel interestChannel;

    public InterestHandlerImpl(SourcedEurekaRegistry<InstanceInfo> registry, ClientChannelFactory channelFactory) {
        this.registry = registry;
        this.interestChannel = channelFactory.newInterestChannel();
        this.isShutdown = new AtomicBoolean(false);
    }

    /**
     * TODO: is the conversion necessary? This would affect equals which might be necessary
     * Get interest stream from the registry, and additionally convert from sourced notifications to base notifications
     * if necessary. We don't want to expose "sourced" types to client users.
     */
    private Observable<ChangeNotification<InstanceInfo>> forInterestFromRegistry(final Interest<InstanceInfo> interest) {
        return registry.forInterest(interest)
                .map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof SourcedChangeNotification) {
                            return ((SourcedChangeNotification<InstanceInfo>) notification).toBaseNotification();
                        } else if (notification instanceof SourcedModifyNotification) {
                            return ((SourcedModifyNotification<InstanceInfo>) notification).toBaseNotification();
                        }
                        return notification;
                    }
                });
    }


    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        final Observable reply = interestChannel.appendInterest(interest)
                .cast(ChangeNotification.class)
                .mergeWith(forInterestFromRegistry(interest))
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends ChangeNotification>>() {
                    @Override
                    public Observable<? extends ChangeNotification> call(Throwable throwable) {
                        if (isShutdown.get()) {
                            return Observable.empty();
                        } else {
                            return Observable.error(throwable);
                        }
                    }
                })
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        interestChannel.removeInterest(interest).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.warn("forInterest unsubscribe failed", e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });
                    }
                });

        return reply;
    }

    @Override
    public void shutdown() {
        isShutdown.set(true);
        interestChannel.close();
        registry.shutdown();
    }
}

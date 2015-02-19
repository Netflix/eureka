package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;

import javax.inject.Inject;

/**
* TODO: use a more sophisticated retry policy that creates channels that connects to different write servers?
*
* @author David Liu
*/
public class EurekaRegistrationClientImpl implements EurekaRegistrationClient {

    private static final Logger logger = LoggerFactory.getLogger(EurekaRegistrationClientImpl.class);

    private static final int DEFAULT_RETRY_WAIT_MILLIS = 500;

    private final RetryableConnectionFactory<RegistrationChannel> retryableConnectionFactory;
    private final int retryWaitMillis;

    @Inject
    public EurekaRegistrationClientImpl(ChannelFactory<RegistrationChannel> channelFactory) {
        this(channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /*visible for testing*/ EurekaRegistrationClientImpl(ChannelFactory<RegistrationChannel> channelFactory, int retryWaitMillis) {
        this.retryableConnectionFactory = new RetryableConnectionFactory<>(channelFactory);
        this.retryWaitMillis = retryWaitMillis;
    }

    @Override
    public RegistrationObservable register(Observable<InstanceInfo> instanceInfoStream) {
        Observable<InstanceInfo> opStream = instanceInfoStream.distinctUntilChanged();
        Func2<RegistrationChannel, InstanceInfo, Observable<Void>> executeOnChannel = new Func2<RegistrationChannel, InstanceInfo, Observable<Void>>() {
            @Override
            public Observable<Void> call(RegistrationChannel channel, InstanceInfo instanceInfo) {
                return channel.register(instanceInfo);
            }
        };
        final RetryableConnection<RegistrationChannel> retryableConnection = retryableConnectionFactory.singleOpConnection(
                opStream,
                executeOnChannel
        );

        Observable<Void> initObservable = retryableConnection.getInitObservable();

        Observable<Void> lifecycle = retryableConnection.getRetryableLifecycle()
                .retryWhen(new RetryStrategyFunc(retryWaitMillis))
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {
                        // unregister
                        retryableConnection.getChannelObservable().flatMap(new Func1<RegistrationChannel, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(RegistrationChannel channel) {
                                return channel.unregister().finallyDo(new Action0() {  // best effort unregister
                                    @Override
                                    public void call() {
                                        retryableConnection.close();
                                    }
                                });
                            }
                        }).subscribe(new NoOpSubscriber<Void>());
                    }
                })
                .share();

        return RegistrationObservable.from(lifecycle, initObservable);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down RegistrationClient");
        // nothing to shutdown
    }
}

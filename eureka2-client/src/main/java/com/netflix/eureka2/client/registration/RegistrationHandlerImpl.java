package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.client.channel.RetryableConnectionFactory;
import com.netflix.eureka2.client.channel.RetryableConnection;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

import javax.inject.Inject;

/**
* TODO: use a more sophisticated retry policy that creates channels that connects to different write servers?
*
* @author David Liu
*/
public class RegistrationHandlerImpl implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationHandlerImpl.class);

    private static final int DEFAULT_RETRY_WAIT_MILLIS = 500;

    private final RetryableConnectionFactory<RegistrationChannel, InstanceInfo, Void> retryableConnectionFactory;
    private final int retryWaitMillis;

    @Inject
    public RegistrationHandlerImpl(ChannelFactory<RegistrationChannel> channelFactory) {
        this(channelFactory, DEFAULT_RETRY_WAIT_MILLIS);
    }

    /*visible for testing*/ RegistrationHandlerImpl(ChannelFactory<RegistrationChannel> channelFactory, int retryWaitMillis) {
        this.retryableConnectionFactory = new RetryableConnectionFactory<RegistrationChannel, InstanceInfo, Void>(channelFactory) {
            @Override
            protected Observable<Void> executeOnChannel(RegistrationChannel channel, InstanceInfo instanceInfo) {
                return channel.register(instanceInfo);
            }

            @Override
            protected Observable<Void> connectToChannelInput(RegistrationChannel channel) {
                return Observable.empty();
            }
        };
        this.retryWaitMillis = retryWaitMillis;
    }

    @Override
    public RegistrationResponse register(Observable<InstanceInfo> instanceInfoStream) {
        final RetryableConnection<RegistrationChannel, Void> retryableConnection
                = retryableConnectionFactory.newConnection(instanceInfoStream.distinctUntilChanged());

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

        return RegistrationResponse.from(lifecycle, initObservable);
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down RegistrationHandler");
        // nothing to shutdown
    }
}

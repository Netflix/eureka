package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.utils.rx.BreakerSwitchSubject;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subjects.AsyncSubject;
import rx.subjects.BehaviorSubject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract factory that provides a {@link RetryableConnection} for each newConnection call.
 *
 * @param <CHANNEL> the type of channel to be used
 * @param <OP> the type of op to be applied to the channel
 *
 * @author David Liu
 */
public abstract class RetryableConnectionFactory<CHANNEL extends ServiceChannel, OP> {

    private static final Logger logger = LoggerFactory.getLogger(RetryableConnectionFactory.class);

    private final ChannelFactory<CHANNEL> channelFactory;

    public RetryableConnectionFactory(final ChannelFactory<CHANNEL> channelFactory) {
        this.channelFactory = channelFactory;
    }

    /**
     * @param opStream an observable stream of ops for the channel to operate on (i.e. Interest, InstanceInfo)
     * @return a {@link RetryableConnection} that contains several observables. This lifecycle observable provided
     * can be retried on.
     */
    public RetryableConnection<CHANNEL> newConnection(final Observable<OP> opStream) {
        final AsyncSubject<Void> initSubject = AsyncSubject.create();  // subject used to cache init status

        final BreakerSwitchSubject<OP> opSubject = BreakerSwitchSubject.create(BehaviorSubject.<OP>create());
        final BreakerSwitchSubject<CHANNEL> channelSubject = BreakerSwitchSubject.create(BehaviorSubject.<CHANNEL>create());

        final Observable<CHANNEL> channelObservable = channelSubject.asObservable()
                .scan(new Func2<CHANNEL, CHANNEL, CHANNEL>() {
                    @Override
                    public CHANNEL call(CHANNEL prev, CHANNEL curr) {
                        if (prev != null) {
                            logger.info("Closing old channel {}", prev);
                            prev.close();
                        }
                        return curr;
                    }
                });
        channelObservable.subscribe(new NoOpSubscriber<CHANNEL>());  // eagerly start the cleanup scan

        final AtomicBoolean opStreamConnected = new AtomicBoolean(false);
        final AtomicBoolean initialConnect = new AtomicBoolean(true);

        Observable<Void> lifecycle = Observable
                .combineLatest(channelObservable, opSubject, new Func2<CHANNEL, OP, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final CHANNEL channel, OP op) {
                        logger.debug("executing on channel {} op {}", channel.toString(), op.toString());
                        executeOnChannel(channel, op).subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                if (initialConnect.compareAndSet(true, false)) {
                                    initSubject.onCompleted();
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                channel.close(e);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                            }
                        });

                        return channel.asLifecycleObservable();
                    }
                })
                .flatMap(new Func1<Observable<Void>, Observable<Void>>() {  // flatmap from Ob<Ob<Void> to Ob<Void>
                    @Override
                    public Observable<Void> call(Observable<Void> observable) {
                        return observable;
                    }
                })
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        channelSubject.onNext(channelFactory.newChannel());
                        if (opStreamConnected.compareAndSet(false, true)) {
                            opStream.subscribe(opSubject);
                        }
                    }
                });

        return new RetryableConnection<>(
                channelSubject.asObservable(),
                lifecycle.asObservable(),
                initSubject.asObservable(),
                new Action0() {  // Why not perform this shutdown on an unsubscribe on the lifecycle?
                    @Override    //  Because we want to be able to call .retry() on it.
                    public void call() {
                        channelSubject.doOnNext(new Action1<CHANNEL>() {
                            @Override
                            public void call(CHANNEL channel) {
                                channel.close();
                            }
                        }).subscribe();
                        channelSubject.close();
                        opSubject.close();
                    }
                }
        );
    }

    protected abstract Observable<Void> executeOnChannel(CHANNEL channel, OP op);
}

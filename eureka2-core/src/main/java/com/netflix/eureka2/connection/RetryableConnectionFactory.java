package com.netflix.eureka2.connection;

import java.util.concurrent.atomic.AtomicBoolean;

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
import rx.subjects.Subject;

/**
 * Factory that provides a {@link RetryableConnection}.
 * @param <CHANNEL> the type of channel to be used
 *
 * @author David Liu
 */
public class RetryableConnectionFactory<CHANNEL extends ServiceChannel> {

    private static final Logger logger = LoggerFactory.getLogger(RetryableConnectionFactory.class);

    protected final ChannelFactory<CHANNEL> channelFactory;

    public RetryableConnectionFactory(final ChannelFactory<CHANNEL> channelFactory) {
        this.channelFactory = channelFactory;
    }

    /**
     * Create a retryable connection that wraps around channels that executes with zero operands.
     * It is assumed also that the channel does not produce any data.
     *
     * @param executeOnChannel a func1 that describes a zero op call for the channel
     * @return a {@link RetryableConnection} that contains several observables. This lifecycle observable provided
     * can be retried on.
     */
    public RetryableConnection<CHANNEL> zeroOpConnection(final Func1<CHANNEL, Observable<Void>> executeOnChannel) {
        Observable<Integer> opStream = Observable.just(1);
        Func2<CHANNEL, Integer, Observable<Void>> adaptedExecute = new Func2<CHANNEL, Integer, Observable<Void>>() {
            @Override
            public Observable<Void> call(CHANNEL channel, Integer integer) {
                return executeOnChannel.call(channel);
            }
        };
        return singleOpConnection(opStream, adaptedExecute);
    }

    /**
     * Create a retryable connection that wraps around channels that executes on single operands
     *
     * @param <OP> the type of op to be applied to the channel
     * @param opStream an observable stream of ops for the channel to operate on (i.e. Interest, InstanceInfo)
     * @param executeOnChannel a func2 that describes the call for the channel on the operations
     * @return a {@link RetryableConnection} that contains several observables. This lifecycle observable provided
     * can be retried on.
     */
    public <OP> RetryableConnection<CHANNEL> singleOpConnection(
            final Observable<OP> opStream,
            final Func2<CHANNEL, OP, Observable<Void>> executeOnChannel) {
        final AsyncSubject<Void> initSubject = AsyncSubject.create();  // subject used to cache init status

        final BreakerSwitchSubject<CHANNEL> channelSubject = BreakerSwitchSubject.create(BehaviorSubject.<CHANNEL>create());

        final Observable<OP> opObservable = opStream.replay(1).refCount();
        final Subscriber<OP> opSubscriber = new NoOpSubscriber<>();
        final Observable<CHANNEL> channelObservable = channelObservableWithCleanUp(channelSubject);

        final AtomicBoolean opStreamConnected = new AtomicBoolean(false);
        final AtomicBoolean initialConnect = new AtomicBoolean(true);

        Observable<Void> lifecycle = Observable
                .combineLatest(channelObservable, opObservable, new Func2<CHANNEL, OP, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final CHANNEL channel, OP op) {
                        logger.debug("executing on channel {} op {}", channel.toString(), op.toString());
                        executeOnChannel.call(channel, op).subscribe(new Subscriber<Void>() {
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
                            // keep a constant subscription to the opStream throughout retries so we don't need to
                            // replay all prev states (if the stream replays)
                            opObservable.subscribe(opSubscriber);
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
                        opSubscriber.unsubscribe();
                    }
                }
        );
    }

    /**
     * @return a channel observable that does clean up of the previous channel every time a new channel is created
     */
    protected Observable<CHANNEL> channelObservableWithCleanUp(Subject<CHANNEL, CHANNEL> channelSubject) {
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

        return channelObservable;
    }
}

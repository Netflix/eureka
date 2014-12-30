package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RetryableEurekaChannelException;
import com.netflix.eureka2.channel.RetryableServiceChannel;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.registry.swap.RegistrySwapOperator;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Tomasz Bak
 */
public class RetryableInterestChannel
        extends RetryableServiceChannel<ClientInterestChannel>
        implements ClientInterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(RetryableInterestChannel.class);

    public static final long DEFAULT_INITIAL_DELAY = 80;

    private static final Throwable CHANNEL_FAILURE = new RetryableEurekaChannelException(
            "There was a communication failure, and connection has been reestablished; a new subscription is required");

    private final InterestTracker interestTracker;
    private final ClientChannelFactory channelFactory;
    private final RegistrySwapStrategyFactory swapStrategyFactory;
    private final EurekaClientMetricFactory metricFactory;

    public RetryableInterestChannel(
            ClientChannelFactory channelFactory,
            RegistrySwapStrategyFactory swapStrategyFactory,
            EurekaClientMetricFactory metricFactory,
            long retryInitialDelayMs,
            Scheduler scheduler) {
        super(channelFactory.newInterestChannel(), retryInitialDelayMs, scheduler);
        this.interestTracker = new InterestTracker();
        this.channelFactory = channelFactory;
        this.metricFactory = metricFactory;
        this.swapStrategyFactory = swapStrategyFactory;
    }


    @Override
    public Observable<Void> change(Interest<InstanceInfo> newInterest) {
        return Observable.error(new UnsupportedOperationException("Not supported for ClientInterestChannel"));
    }

    @Override
    public EurekaClientRegistry<InstanceInfo> associatedRegistry() {
        return currentDelegateChannel().associatedRegistry();
    }

    @Override
    public Observable<Void> appendInterest(final Interest<InstanceInfo> toAppend) {
        return currentDelegateChannelObservable().switchMap(new Func1<ClientInterestChannel, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(ClientInterestChannel clientInterestChannel) {
                return clientInterestChannel.appendInterest(toAppend)
                        .doOnCompleted(interestTracker.createAppendInterestAction(toAppend));
            }
        });
    }

    @Override
    public Observable<Void> removeInterest(final Interest<InstanceInfo> toRemove) {
        return currentDelegateChannelObservable().switchMap(new Func1<ClientInterestChannel, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(ClientInterestChannel clientInterestChannel) {
                return clientInterestChannel.removeInterest(toRemove)
                        .doOnCompleted(interestTracker.createRemoveInterestAction(toRemove));
            }
        });
    }

    @Override
    protected Observable<ClientInterestChannel> reestablish() {
        final ClientInterestChannel newChannel = channelFactory.newInterestChannel();

        final EurekaClientRegistry<InstanceInfo> prevRegistry = currentDelegateChannel().associatedRegistry();
        final EurekaClientRegistry<InstanceInfo> newRegistry = newChannel.associatedRegistry();

        return Observable.create(new Observable.OnSubscribe<ClientInterestChannel>() {
            @Override
            public void call(final Subscriber<? super ClientInterestChannel> subscriber) {
                try {
                    // Resubscribe
                    Interest<InstanceInfo> activeInterests = new MultipleInterests<InstanceInfo>(interestTracker.interests.keySet());
                    newChannel.appendInterest(activeInterests).subscribe();

                    // Wait until registry fills up to the expected level.
                    newRegistry.forInterest(activeInterests)
                            .lift(new RegistrySwapOperator(prevRegistry, newRegistry, swapStrategyFactory))
                            .doOnCompleted(new Action0() {
                                @Override
                                public void call() {
                                    subscriber.onNext(newChannel);  // onNext triggers shutdown of old delegate channel, which will shutdown old registry
                                    subscriber.onCompleted();
                                }
                            })
                            .subscribe();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    @Override
    protected void _close() {
        super._close();
        channelFactory.shutdown();
        interestTracker.close();
    }

    /**
     * {@link InterestTracker} keeps a reference counted collection of interests, so in case of channel failure
     * we can silently reconnect and re-subscribe.
     */
    class InterestTracker {

        // We use concurrent map here as this map is modified when request acknowledgement completes by a
        // different thread that the one doing the reconnect. Updates are however serialized, as come from the
        // channel event loop.
        final Map<Interest<InstanceInfo>, Integer> interests = new ConcurrentHashMap<>();

        public Action0 createResetAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    interests.put(interest, 1);
                }
            };
        }

        Action0 createAppendInterestAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    if (interests.containsKey(interest)) {
                        interests.put(interest, interests.get(interest) + 1);
                    } else {
                        interests.put(interest, 1);
                    }
                }
            };
        }

        Action0 createRemoveInterestAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    if (interests.containsKey(interest)) {
                        int newLevel = interests.get(interest) - 1;
                        if (newLevel == 0) {
                            interests.remove(interest);
                        } else {
                            interests.put(interest, newLevel);
                        }
                    }
                }
            };
        }

        void close() {
            interests.clear();
        }
    }
}

package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.channel.RetryableEurekaChannelException;
import com.netflix.eureka2.channel.RetryableServiceChannel;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Note that operations (appendInterest and removeInterest) on this interest channel must be serialized by an external
 * guarantee (such as using an {@link com.netflix.eureka2.client.channel.InterestChannelInvoker}).
 *
 * @author Tomasz Bak
 */
public class RetryableInterestChannel
        extends RetryableServiceChannel<ClientInterestChannel>
        implements ClientInterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(RetryableInterestChannel.class);

    public static final long DEFAULT_INITIAL_DELAY = 80;

    private static final Throwable CHANNEL_FAILURE = new RetryableEurekaChannelException(
            "There was a communication failure, and connection has been reestablished; a new subscription is required");

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final InterestTracker interestTracker;
    private final Func1<SourcedEurekaRegistry<InstanceInfo>, ClientInterestChannel> channelFactory;

    public RetryableInterestChannel(
            Func1<SourcedEurekaRegistry<InstanceInfo>, ClientInterestChannel> channelFactory,
            final SourcedEurekaRegistry<InstanceInfo> registry,
            long retryInitialDelayMs,
            Scheduler scheduler) {
        this(channelFactory, registry, new InterestTracker(), retryInitialDelayMs, scheduler);
    }

    /* visible for testing */ RetryableInterestChannel(
            Func1<SourcedEurekaRegistry<InstanceInfo>, ClientInterestChannel> channelFactory,
            final SourcedEurekaRegistry<InstanceInfo> registry,
            InterestTracker interestTracker,
            long retryInitialDelayMs,
            Scheduler scheduler) {
        super(channelFactory.call(registry), retryInitialDelayMs, scheduler);
        this.registry = registry;
        this.interestTracker = interestTracker;
        this.channelFactory = channelFactory;
    }

    @Override
    public Observable<Void> change(Interest<InstanceInfo> newInterest) {
        return Observable.error(new UnsupportedOperationException("Not supported for ClientInterestChannel"));
    }

    @Override
    public SourcedEurekaRegistry<InstanceInfo> associatedRegistry() {
        return currentDelegateChannel().associatedRegistry();
    }

    @Override
    public Observable<Void> appendInterest(final Interest<InstanceInfo> toAppend) {
        return currentDelegateChannelObservable().switchMap(new Func1<ClientInterestChannel, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(ClientInterestChannel clientInterestChannel) {
                // eagerly append to the interestTracker as we want to keep track of the append regardless of the
                // success or failure of the actual call
                interestTracker.appendInterest(toAppend);
                return clientInterestChannel.appendInterest(toAppend);
            }
        });
    }

    @Override
    public Observable<Void> removeInterest(final Interest<InstanceInfo> toRemove) {
        return currentDelegateChannelObservable().switchMap(new Func1<ClientInterestChannel, Observable<? extends Void>>() {
            @Override
            public Observable<? extends Void> call(ClientInterestChannel clientInterestChannel) {
                // eagerly remove from the interestTracker as we want to keep track of the remove regardless of the
                // success or failure of the actual call
                interestTracker.removeInterest(toRemove);
                return clientInterestChannel.removeInterest(toRemove);
            }
        });
    }

    @Override
    protected Observable<ClientInterestChannel> reestablish() {
        return registry.evictAll()  // evict everything in the registry (just in case there are stale copies)
                .switchMap(new Func1<Long, Observable<ClientInterestChannel>>() {
                    @Override
                    public Observable<ClientInterestChannel> call(Long count) {
                        logger.info("Evicted copies from {} registry entries", count);

                        Interest<InstanceInfo> activeInterests = new MultipleInterests<>(interestTracker.interests.keySet());

                        final ClientInterestChannel newChannel = channelFactory.call(registry);
                        newChannel.appendInterest(activeInterests).subscribe();
                        return Observable.just(newChannel);
                    }
                });
    }

    @Override
    protected void _close() {
        interestTracker.close();
        super._close();
    }

    @Override
    public Source getSource() {
        return currentDelegateChannel().getSource();
    }

    /**
     * {@link InterestTracker} keeps a reference counted collection of interests, so in case of channel failure
     * we can silently reconnect and re-subscribe.
     *
     * Note that we don't need to deal with concurrency on the counts in the map as the append and remove operations
     * are serialized by external guarantees.
     */
    static class InterestTracker {

        // We use concurrent map here as this map is modified when request acknowledgement completes by a
        // different thread that the one doing the reconnect. Updates are however serialized, as come from the
        // channel event loop.
        final ConcurrentMap<Interest<InstanceInfo>, Integer> interests = new ConcurrentHashMap<>();

        void appendInterest(final Interest<InstanceInfo> interest) {
            Integer curr = interests.putIfAbsent(interest, 1);
            if (curr != null) {
                interests.put(interest, curr + 1);
            }
        }

        void removeInterest(final Interest<InstanceInfo> interest) {
            Integer curr = interests.get(interest);
            if (curr != null) {
                if (curr <= 1) {
                    interests.remove(interest);
                } else {
                    interests.put(interest, curr - 1);
                }
            }
        }

        void close() {
            interests.clear();
        }
    }
}

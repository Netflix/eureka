package com.netflix.eureka2.client.registry;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.channel.consumer.RetryableChannelConsumer;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistryProxy.RegistryTracker;
import com.netflix.eureka2.client.registry.swap.RegistrySwapOperator;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.service.InterestChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.observers.Subscribers;
import rx.schedulers.Schedulers;

/**
 * An implementation of {@link EurekaRegistry} to be used by the eureka client.
 *
 * This registry abstracts the {@link InterestChannel} interaction from the consumers of this registry and transparently
 * reconnects when a channel is broken.
 *
 * <h2>Storage</h2>
 *
 * This registry uses {@link EurekaClientRegistryImpl} for actual data storage.
 *
 * <h2>Reconnects</h2>
 *
 * Whenever the used {@link InterestChannel} is broken, this class holds the last known registry information till the
 * time it is successfully able to reconnect and relay the last know interest set to the new {@link InterestChannel}.
 * On a successful reconnect, the old registry data is disposed and the registry is created afresh from the instance
 * stream from the new {@link InterestChannel}
 *
 * @author Nitesh Kant
 */
@Singleton
public class EurekaClientRegistryProxy
        extends RetryableChannelConsumer<ClientInterestChannel, RegistryTracker>
        implements EurekaClientRegistry<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaClientRegistryProxy.class);

    public static final long DEFAULT_INITIAL_DELAY = 80;
    private static final Throwable CHANNEL_FAILURE = new Exception(
            "There was a communication failure, and connection has been reestablished; a new subscription is required");

    private final ClientChannelFactory channelFactory;
    private final RegistrySwapStrategyFactory swapStrategyFactory;
    private final EurekaClientMetricFactory metricFactory;

    @Inject
    public EurekaClientRegistryProxy(ClientChannelFactory channelFactory,
                                     RegistrySwapStrategyFactory swapStrategyFactory,
                                     long retryInitialDelayMs,
                                     EurekaClientMetricFactory metricFactory) {
        this(channelFactory, swapStrategyFactory, retryInitialDelayMs, metricFactory, Schedulers.computation());
    }

    public EurekaClientRegistryProxy(ClientChannelFactory channelFactory,
                                     RegistrySwapStrategyFactory swapStrategyFactory,
                                     long retryInitialDelayMs,
                                     EurekaClientMetricFactory metricFactory,
                                     Scheduler scheduler) {
        super(retryInitialDelayMs, scheduler);
        this.channelFactory = channelFactory;
        this.metricFactory = metricFactory;
        this.swapStrategyFactory = swapStrategyFactory;
        initializeRetryableConsumer();
    }


    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        throw new IllegalArgumentException("Proxy registry modification not allowed");
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        throw new IllegalArgumentException("Proxy registry modification not allowed");
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        throw new IllegalArgumentException("Proxy registry modification not allowed");
    }

    @Override
    public int size() {
        return getCurrentRegistry().size();
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        return getCurrentRegistry().forSnapshot(interest);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        StateWithChannel current = getStateWithChannel();

        ClientInterestChannel currentChannel = current.getChannel();
        RegistryTracker currentState = current.getState();

        Observable toReturn = currentChannel
                .appendInterest(interest).cast(ChangeNotification.class)
                .doOnCompleted(currentState.createAddInterestAction(interest))
                .mergeWith(currentState.registry.forInterest(interest))
                .doOnTerminate(currentState.createRemoveInterestAction(interest));

        return toReturn;
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.create(new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                shutdownRetryableConsumer();
                channelFactory.shutdown();  // channelFactory will shutdown registry and transport clients
            }
        });
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        throw new IllegalArgumentException("EurekaClientRegistryProxy supports regular shutdown only");
    }

    @Override
    protected StateWithChannel reestablish() {
        final RegistryTracker newState = new RegistryTracker();
        final ClientInterestChannel newChannel = channelFactory.newInterestChannel(newState.registry);
        return new StateWithChannel(newChannel, newState);
    }

    @Override
    protected Observable<Void> repopulate(StateWithChannel newStateWithChannel) {
        final RegistryTracker previousState = getStateWithChannel().getState();
        final RegistryTracker newState = newStateWithChannel.getState();
        final ClientInterestChannel newChannel = newStateWithChannel.getChannel();

        return Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(final Subscriber<? super Void> subscriber) {
                // Resubscribe
                Interest<InstanceInfo> activeInterests = new MultipleInterests<InstanceInfo>(previousState.interests.keySet());
                newChannel.change(activeInterests).subscribe();

                // Wait until registry fills up to the expected level.
                newState.registry.forInterest(activeInterests).lift(
                        new RegistrySwapOperator(previousState.registry, newState.registry, swapStrategyFactory)
                ).subscribe(Subscribers.from(subscriber));
            }
        });

    }

    @Override
    protected void release(StateWithChannel oldState) {
        oldState.getState().registry.shutdown(CHANNEL_FAILURE);
    }

    private EurekaClientRegistry<InstanceInfo> getCurrentRegistry() {
        return getStateWithChannel().getState().registry;
    }

    /**
     * {@link RegistryTracker} keeps a reference counted collection of interests, so in case of channel failure
     * we can silently reconnect and re-subscribe.
     */
    class RegistryTracker {

        final EurekaClientRegistry<InstanceInfo> registry = new EurekaClientRegistryImpl(metricFactory.getRegistryMetrics());
        final ConcurrentMap<Interest<InstanceInfo>, AtomicInteger> interests = new ConcurrentHashMap<>();

        Action0 createAddInterestAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    AtomicInteger newCounter = new AtomicInteger(1);
                    AtomicInteger currentCounter = interests.putIfAbsent(interest, newCounter);
                    if (currentCounter != null) {
                        currentCounter.incrementAndGet();
                    }
                }
            };
        }

        Action0 createRemoveInterestAction(final Interest<InstanceInfo> interest) {
            return new Action0() {
                @Override
                public void call() {
                    AtomicInteger currentCounter = interests.get(interest);
                    if (currentCounter != null) {
                        currentCounter.decrementAndGet();
                    }
                }
            };
        }
    }
}

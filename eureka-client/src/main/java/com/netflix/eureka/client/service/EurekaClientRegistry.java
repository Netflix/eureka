package com.netflix.eureka.client.service;

import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.EurekaRegistryImpl;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

import javax.inject.Inject;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An implementation of {@link EurekaRegistry} to be used by the eureka client.
 *
 * This registry abstracts the {@link InterestChannel} interaction from the consumers of this registry and transparently
 * reconnects when a channel is broken.
 *
 * <h2>Storage</h2>
 *
 * This registry uses {@link EurekaRegistryImpl} for actual data storage.
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
public class EurekaClientRegistry implements EurekaRegistry<InstanceInfo> {

    private final TransportClient readServerClient;
    private final AtomicReference<RegistryState> state;

    @Inject
    public EurekaClientRegistry(final TransportClient readServerClient) {
        this.readServerClient = readServerClient;
        state = new AtomicReference<>(new RegistryState(readServerClient));
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return state.get().registry.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        return state.get().registry.unregister(instanceId);
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        return state.get().registry.update(updatedInfo, deltas);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        final RegistryState currentState = state.get();
        if (currentState.claimRegistration()) {
            return currentState.interestChannel
                    .register(interest)
                    .map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                        @Override
                        public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                            if (currentState.state.compareAndSet(RegistryState.State.RegistrationStarted,
                                                                 RegistryState.State.RegistrationQueued)) {
                                currentState.registrationQueuedAck.onCompleted();
                            }
                            return notification;
                        }
                    }).doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            currentState.registrationQueuedAck.onError(throwable);
                        }
                    });
        } else {
            /**
             * The following code lazily does append on the channel after the registration is successful.
             * Although {@link InterestChannelImpl#appendInterest(Interest)} isn't lazy, this will always be flowing
             * through {@link InterestChannelInvoker#appendInterest(Interest)} which is lazy and hence guarantees that
             * append will not enqueue before register.
             */
            Observable toReturn = currentState.registrationQueuedAck
                    .concatWith(currentState.interestChannel.appendInterest(interest))
                    .cast(ChangeNotification.class)
                    .concatWith(currentState.registry.forInterest(interest));

            return (Observable<ChangeNotification<InstanceInfo>>) toReturn;
        }
    }

    @Override
    public Observable<Void> shutdown() {
        return state.get().shutdown();
    }

    private static final class RegistryState {

        private enum State {
            Idle,
            RegistrationStarted,
            RegistrationQueued /*Terminal state*/
        }

        private final EurekaService service;
        private final EurekaRegistryImpl registry;
        private final ClientInterestChannel interestChannel;
        private final AtomicReference<State> state;

        /**
         * Although {@link InterestChannelInvoker} sequences all operations it does not guarantee that any updates are
         * automatically converted to registration (which is good from the channel point of view). So, we need to make
         * sure that registration and appends are not inter-leaved and hence possibly getting into a state where
         * register arrives before update.
         *
         * This subject replays the result of whether the registration was queued into the
         * {@link InterestChannelInvoker}. If there is an error in registration, subsequent registration will reset this
         * {@link ReplaySubject}
         */
        private ReplaySubject<Void> registrationQueuedAck = ReplaySubject.create();

        private RegistryState(final TransportClient readServerClient) {
            registry = new EurekaRegistryImpl();
            service = EurekaServiceImpl.forReadServer(registry, readServerClient);
            interestChannel = (ClientInterestChannel) service.newInterestChannel();
            state = new AtomicReference<>(State.Idle);
        }

        private boolean claimRegistration() {
            return state.compareAndSet(State.Idle, State.RegistrationStarted);
        }

        private Observable<Void> shutdown() {
            service.shutdown();
            interestChannel.close();
            return registry.shutdown();
        }
    }
}

package com.netflix.eureka2.testkit.junit.stubs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.junit.stubs.EurekaRegistrationClientStub.RegistrationTracker.State;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.subjects.ReplaySubject;

/**
 * Stub implementation of {@link EurekaRegistrationClient} for unit testing purposes.
 * This class is not thread safe.
 *
 * @author Tomasz Bak
 */
public class EurekaRegistrationClientStub implements EurekaRegistrationClient {

    private int registrationIdCounter;

    private final List<Observable<RegistrationStatus>> pendingRegistrations = new ArrayList<>();
    private final Map<Observable<RegistrationStatus>, RegistrationTracker> registrationTrackers = new HashMap<>();

    @Override
    public Observable<RegistrationStatus> register(Observable<InstanceInfo> registrant) {
        final RegistrationTracker registrationTracker = new RegistrationTracker(registrant);

        OnSubscribe<Void> onSubscribe = new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                registrationTracker.doSubscribe(subscriber);
            }
        };
        Observable<Void> initObservable = Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                registrationTracker.doSubscribeOnInit(subscriber);
            }
        });
        String id = "id#" + registrationIdCounter;
        registrationIdCounter++;
        Observable<RegistrationStatus> registrationObservable = null;

        pendingRegistrations.add(registrationObservable);
        registrationTrackers.put(registrationObservable, registrationTracker);

        // FIXME This requires some refactoring
        return Observable.empty();
    }

    @Override
    public void shutdown() {
        for (RegistrationTracker tracker : registrationTrackers.values()) {
            tracker.shutdown();
        }
        registrationTrackers.clear();
        pendingRegistrations.clear();
    }

    public List<Observable<RegistrationStatus>> getPendingRegistrations() {
        return new ArrayList<>(pendingRegistrations);
    }

    public boolean hasPendingRegistrations() {
        return !pendingRegistrations.isEmpty();
    }

    public boolean hasSubscribedRegistrations() {
        for (RegistrationTracker tracker : registrationTrackers.values()) {
            if (tracker.getState() == State.Subscribed) {
                return true;
            }
        }
        return false;
    }

    public InstanceInfo getLastRegistrationUpdate() {
        if (registrationTrackers.isEmpty()) {
            return null;
        }
        Observable<RegistrationStatus> lastRegistration = pendingRegistrations.get(pendingRegistrations.size() - 1);
        List<InstanceInfo> updates = registrationTrackers.get(lastRegistration).getReceivedUpdates();
        return updates == null || updates.isEmpty() ? null : updates.get(updates.size() - 1);
    }

    public List<InstanceInfo> getLastRegistrationUpdates(Observable<RegistrationStatus> registrationObservable) {
        RegistrationTracker tracker = registrationTrackers.get(registrationObservable);
        if (tracker == null || tracker.getReceivedUpdates() == null) {
            return null;
        }
        return new ArrayList<>(tracker.getReceivedUpdates());
    }

    static class RegistrationTracker {

        enum State {Idle, Subscribed, OnError, OnComplete}

        private final Observable<InstanceInfo> registrant;

        private State state = State.Idle;
        private final List<State> subscriptionHistory = new ArrayList<>();

        private Subscription registrantSubscription;
        private List<InstanceInfo> receivedUpdates;
        private ReplaySubject<Void> registrationSubject;
        private ReplaySubject<Void> initRegistrationSubject;

        RegistrationTracker(Observable<InstanceInfo> registrant) {
            this.registrant = registrant;
        }

        public State getState() {
            return state;
        }

        List<State> getSubscriptionHistory() {
            return subscriptionHistory;
        }

        List<InstanceInfo> getReceivedUpdates() {
            return receivedUpdates;
        }

        void doSubscribe(Subscriber<? super Void> subscriber) {
            initialize();
            registrationSubject
                    .doOnUnsubscribe(new Action0() {
                        @Override
                        public void call() {
                            registrantSubscription.unsubscribe();
                            if (initRegistrationSubject.hasObservers()) {
                                initRegistrationSubject.onError(new Exception("Registration request unsubscribed before first registration succeeded"));
                            }
                            reset(null);
                        }
                    })
                    .subscribe(subscriber);
        }

        void doSubscribeOnInit(Subscriber<? super Void> subscriber) {
            initialize();
            initRegistrationSubject.subscribe(subscriber);
        }

        void shutdown() {
            reset(null);
        }

        private void initialize() {
            if (state == State.Idle) {
                state = State.Subscribed;
                registrationSubject = ReplaySubject.create();
                initRegistrationSubject = ReplaySubject.create();
                receivedUpdates = new ArrayList<>();
                registrantSubscription = registrant.subscribe(new Subscriber<InstanceInfo>() {
                    @Override
                    public void onCompleted() {
                        // No-op
                    }

                    @Override
                    public void onError(Throwable e) {
                        reset(e);
                    }

                    @Override
                    public void onNext(InstanceInfo instanceInfo) {
                        receivedUpdates.add(instanceInfo);
                        if (receivedUpdates.size() == 1) {
                            initRegistrationSubject.onCompleted();
                        }
                    }
                });
            }
        }

        private void reset(Throwable e) {
            subscriptionHistory.add(e == null ? State.OnComplete : State.OnError);
            state = State.Idle;

            registrantSubscription.unsubscribe();
            registrationSubject.onCompleted();
            initRegistrationSubject.onCompleted();

            registrantSubscription = null;
            receivedUpdates = null;
            registrationSubject = null;
            initRegistrationSubject = null;
        }
    }
}

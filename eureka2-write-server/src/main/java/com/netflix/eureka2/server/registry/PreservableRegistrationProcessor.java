package com.netflix.eureka2.server.registry;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.Sourced;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import com.netflix.eureka2.utils.rx.RxFunctions;
import com.netflix.eureka2.utils.rx.SettableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

/**
 * @author Tomasz Bak
 */
public class PreservableRegistrationProcessor implements EurekaRegistrationProcessor<InstanceInfo>, Sourced {

    private static final Logger logger = LoggerFactory.getLogger(PreservableRegistrationProcessor.class);

    protected final EurekaRegistry<InstanceInfo> registry;

    private final PublishSubject<Observable<SourcedChangeNotification<InstanceInfo>>> registrationSubject = PublishSubject.create();
    private final BehaviorSubject<Integer> sizeSubject = BehaviorSubject.create();

    private final Observable<ChangeNotification<InstanceInfo>> registrationStream;
    private final Subscriber<Void> processorSubscriber;
    private final Source selfSource;

    // for eviction use
    private final EurekaRegistryConfig registryConfig;
    private final QuotaSubscriber quotaSubscriber;

    private volatile EvictionQuotaKeeper evictionQuotaKeeper;

    /**
     * All registrations are serialized, and each registration subscription updates this data structure. If there is
     * another subscription with the given id, it is ignored.
     * The source sent from the Registration channels is unique per instanceId:connection.
     */
    private final ConcurrentMap<String, Registrant> approvedRegistrations = new ConcurrentHashMap<>();

    private final AtomicBoolean isShutdown = new AtomicBoolean();

    @Inject
    public PreservableRegistrationProcessor(EurekaRegistry registry, EurekaRegistryConfig registryConfig) {
        this(registry, registryConfig, new QuotaSubscriber());
    }

    public PreservableRegistrationProcessor(EurekaRegistry registry, EurekaRegistryConfig registryConfig, QuotaSubscriber quotaSubscriber) {
        this.registry = registry;
        this.registryConfig = registryConfig;
        this.quotaSubscriber = quotaSubscriber;

        this.selfSource = registry.getSource();
        this.processorSubscriber = new LoggingSubscriber<>(logger);

        this.evictionQuotaKeeper = new EvictionQuotaKeeperImpl(this, registryConfig);

        this.registrationStream = Observable.merge(registrationSubject)
                .map(new Func1<SourcedChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(SourcedChangeNotification<InstanceInfo> notification) {
                        Source thisSource = notification.getSource();
                        if (notification.isDataNotification()) {
                            // first check to see if this is a notification from the latest "generation" for the given id
                            // if not, do nothing
                            Registrant latest = approvedRegistrations.get(notification.getSource().getName());
                            if (latest == null || !latest.source.equals(thisSource)) {
                                return null;
                            }

                            // if this is a deletion, remove from the approvedRegistrationsMap as well
                            if (notification.getKind() == ChangeNotification.Kind.Delete) {
                                Registrant registrant = approvedRegistrations.remove(thisSource.getName());
                                registrant.settableSubscriber.setOnComplete();  // safe to do
                            }

                            // finally update size and move on
                            sizeSubject.onNext(approvedRegistrations.size());
                            return notification.toBaseNotification();
                        } else {
                            logger.warn("Should not see StreamStateNotification in registration processor {}", notification);
                            return null;
                        }
                    }
                })
                .filter(RxFunctions.filterNullValuesFunc())
                .onErrorResumeNext(Observable.<ChangeNotification<InstanceInfo>>empty());  // ignore all errors
    }

    @PostConstruct
    public void init() {
        evictionQuotaKeeper.quota().subscribe(quotaSubscriber);
        registry.connect(selfSource, registrationStream).subscribe(processorSubscriber);
    }

    /* visible for testing*/ void setEvictionQuotaKeeper(EvictionQuotaKeeper newEvictionQuotaKeeper) {
        this.evictionQuotaKeeper = newEvictionQuotaKeeper;
    }

    @Override
    public Observable<Void> shutdown() {
        _shutdown();
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        _shutdown();
        return Observable.error(cause);
    }

    private boolean _shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            processorSubscriber.unsubscribe();
            quotaSubscriber.unsubscribe();
            return true;
        }
        return false;
    }

    /**
     * This connect method has the following contract:
     * - for duplicate connects with the same id
     *   - connect the new stream and close() the old stream immediately (can this lead to races where we see a brief (Delete,Add) notification set?
     *
     * - for registrationUpdates finishing with onComplete,
     *   - can only happen at graceful channel unsubcribe, so is fine (make sure channel sent an unregister first?)
     *
     * - for registrationUpdates finishing with onError,
     *   - the channel would have already close(e)d. We "don't" want to complete the stream in this case immediately.
     *   - only onComplete the stream only when we evict at eviction time
     */
    @Override
    public Observable<Void> connect(String id, final Source source, final Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
        final SettableSubscriber<SourcedChangeNotification<InstanceInfo>> settableSubscriber = new SettableSubscriber<>();

        final Observable<SourcedChangeNotification<InstanceInfo>> monitoredUpdates = Observable.just(source)
                .switchMap(new Func1<Source, Observable<? extends ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<? extends ChangeNotification<InstanceInfo>> call(Source aSource) {
                        String id = aSource.getName();
                        final Registrant registrant = new Registrant(aSource, settableSubscriber);
                        Registrant prevRegistrant = approvedRegistrations.put(id, registrant);
                        if (prevRegistrant != null) {
                            quotaSubscriber.addToEvictionQueue(registrant);
                        }
                        return registrationUpdates
                                .materialize()
                                .concatMap(new Func1<Notification<ChangeNotification<InstanceInfo>>, Observable<? extends ChangeNotification<InstanceInfo>>>() {
                                    @Override
                                    public Observable<? extends ChangeNotification<InstanceInfo>> call(Notification<ChangeNotification<InstanceInfo>> rxNotification) {
                                        switch (rxNotification.getKind()) {
                                            case OnNext:
                                                return Observable.just(rxNotification.getValue());
                                            case OnCompleted:
                                                // for onCompleted send an unregister just in case. If one was already seen this is a no-op
                                                unregisterAndComplete(registrant);
                                                break;
                                            case OnError:
                                                quotaSubscriber.addToEvictionQueue(registrant);
                                                break;
                                            default:  // cannot be here
                                        }
                                        return Observable.empty();
                                    }
                                });
                    }
                })
                .map(new Func1<ChangeNotification<InstanceInfo>, SourcedChangeNotification<InstanceInfo>>() {
                    @Override
                    public SourcedChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                        return new SourcedChangeNotification<>(notification, source);
                    }
                })
                .lift(new Observable.Operator<SourcedChangeNotification<InstanceInfo>, SourcedChangeNotification<InstanceInfo>>() {
                    @Override
                    public Subscriber<? super SourcedChangeNotification<InstanceInfo>> call(Subscriber<? super SourcedChangeNotification<InstanceInfo>> subscriber) {
                        settableSubscriber.setWrapped(subscriber);
                        return settableSubscriber;
                    }
                });

        return Observable.<Void>never()  // TODO can we do better than Observable.never()?
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        registrationSubject.onNext(monitoredUpdates);
                    }
                })
                .share();  // TODO overly careful here? Can remove if we ensure only 1 subscriber
    }

    @Override
    public Observable<Integer> sizeObservable() {
        return sizeSubject.distinctUntilChanged();
    }

    @Override
    public int size() {
        return approvedRegistrations.size();
    }

    @Override
    public Source getSource() {
        return selfSource;
    }

    private static void unregisterAndComplete(Registrant registrant) {
        InstanceInfo infoForDelete = new InstanceInfo.Builder()
                .withId(registrant.source.getName())
                .build();
        registrant.settableSubscriber.onNext(new SourcedChangeNotification<>(
                ChangeNotification.Kind.Delete, infoForDelete, registrant.source));
        registrant.settableSubscriber.setOnComplete();
    }

    static class QuotaSubscriber extends Subscriber<Long> {

        final Queue<Registrant> registrationsToEvict = new ConcurrentLinkedDeque<>();

        void addToEvictionQueue(Registrant registrant) {
            registrationsToEvict.add(registrant);
            request(1);
        }

        @Override
        public void onStart() {
            request(0);
        }

        @Override
        public void onCompleted() {
            logger.info("Eviction quota subscription onCompleted");
        }

        @Override
        public void onError(Throwable e) {
            logger.error("Eviction quota subscription terminated with an error", e);
        }

        @Override
        public void onNext(Long quota) {
            for (int i = 0; i < quota; i++) {
                Registrant registrant = registrationsToEvict.poll();
                if (registrant != null) {
                    // send a delete notification, then end
                    unregisterAndComplete(registrant);
                } else {
                    break;
                }
            }
        }
    }

    static class Registrant {
        final Source source;
        final SettableSubscriber<SourcedChangeNotification<InstanceInfo>> settableSubscriber;

        Registrant(Source source, SettableSubscriber<SourcedChangeNotification<InstanceInfo>> settableSubscriber) {
            this.source = source;
            this.settableSubscriber = settableSubscriber;
        }
    }
}

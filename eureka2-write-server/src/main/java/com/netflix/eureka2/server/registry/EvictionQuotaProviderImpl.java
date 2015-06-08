package com.netflix.eureka2.server.registry;

import javax.inject.Inject;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.WriteServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * A quota provider which target is to restore the original registry size
 * from the time the first eviction request is received.
 * The following algorithm is applied when the latter case happens:
 * <ul>
 *     <li>1. eviction quota is calculated as a % of current registry size</li>
 *     <li>2. eviction grants are permitted up to the quota limit</li>
 *     <li>3. until registry size is restored, quota limit is not changed</li>
 * </ul>
 * <p>
 * This is first implementation, but other can be envisaged:
 * <ul>
 *     <li>eviction grants are issued at a configurable rate</li>
 *     <li>erasure policy can be applied to compensate for ungraceful shutdowns</li>
 *     <li>delayed eviction strategy to differentiate singular disconnects from bulk once</li>
 *     <li>adaptive eviction strategy based on the drop out rate</li>
 *     <li>cross check analysis of local registrations vs registry holder copies to better interact with replication channels</li>
 * </ul>
 *
 * @author Tomasz Bak
 */
public class EvictionQuotaProviderImpl implements EvictionQuotaProvider {

    private static final Logger logger = LoggerFactory.getLogger(EvictionQuotaProviderImpl.class);

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final int allowedPercentageDrop;

    private final Semaphore quotaRequests = new Semaphore(0);
    private final Subject<Long, Long> quotaSubject = new SerializedSubject<>(PublishSubject.<Long>create());

    private final Subscription interestSubscription;

    private final AtomicReference<EvictionState> evictionStateRef = new AtomicReference<>();

    @Inject
    public EvictionQuotaProviderImpl(final SourcedEurekaRegistry registry, WriteServerConfig config) {
        this.registry = registry;
        this.allowedPercentageDrop = config.getEvictionAllowedPercentageDrop();

        /**
         * Registry size change should trigger state evaluation, as it may generate eviction permits.
         */
        interestSubscription = this.registry.forInterest(Interests.forFullRegistry()).skipWhile(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(ChangeNotification<InstanceInfo> notification) {
                // Skip first snapshot buffer
                return notification.isDataNotification();
            }
        }).map(new Func1<ChangeNotification<InstanceInfo>, Integer>() {
            @Override
            public Integer call(ChangeNotification<InstanceInfo> notification) {
                return registry.size();
            }
        }).distinct().subscribe(new Subscriber<Integer>() {
            @Override
            public void onCompleted() {
                logger.info("Interest subscription completed with onCompleted. Registry updates will no longer be handled");
            }

            @Override
            public void onError(Throwable e) {
                logger.error("Interest subscription completed with an error. Registry updates will no longer be handled", e);
            }

            @Override
            public void onNext(Integer registrySize) {
                evaluate();
            }
        });
    }

    @Override
    public Observable<Long> quota() {
        return Observable.create(new OnSubscribe<Long>() {
            @Override
            public void call(Subscriber<? super Long> subscriber) {
                subscriber.setProducer(new Producer() {
                    @Override
                    public void request(long n) {
                        quotaRequests.release((int) n);
                        evaluate();
                    }
                });
                quotaSubject.subscribe(subscriber);
            }
        });
    }

    public void shutdown() {
        interestSubscription.unsubscribe();
    }

    /**
     * This method may be execute by quota and interest channel subscriptions concurrently.
     */
    private void evaluate() {
        if (evictionStateRef.get() == null && quotaRequests.availablePermits() == 0) {
            return;
        }

        synchronized (evictionStateRef) {
            if (quotaRequests.availablePermits() == 0) {
                evictionStateRef.getAndSet(null);
                return;
            }
            if (evictionStateRef.get() == null) {
                evictionStateRef.set(new EvictionState());
            }
        }
        EvictionState evictionState = evictionStateRef.get();
        while (evictionState.isEvictionAllowed() && quotaRequests.tryAcquire()) {
            quotaSubject.onNext(1L);
        }
    }

    /**
     * This class encapsulates state related to pending evictions.
     */
    class EvictionState {
        private final int minRegistrySize;

        EvictionState() {
            this.minRegistrySize = registry.size() * allowedPercentageDrop / 100;
        }

        public boolean isEvictionAllowed() {
            return registry.size() > minRegistrySize;
        }
    }
}

package com.netflix.eureka2.server.registry;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.config.EurekaRegistryConfig;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Subscriber;
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
public class EvictionQuotaKeeperImpl implements EvictionQuotaKeeper {

    private static final Logger logger = LoggerFactory.getLogger(EvictionQuotaKeeperImpl.class);

    protected final Semaphore quotaRequests = new Semaphore(0);

    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor;
    private final EurekaRegistryConfig config;

    private final Subscriber<Integer> sizeSubscriber;

    private final Subject<Long, Long> quotaSubject = new SerializedSubject<>(PublishSubject.<Long>create());

    private final AtomicReference<EvictionState> evictionStateRef = new AtomicReference<>();

    public EvictionQuotaKeeperImpl(final EurekaRegistrationProcessor registrationProcessor, EurekaRegistryConfig config) {
        this.registrationProcessor = registrationProcessor;
        this.config = config;
        this.sizeSubscriber = new Subscriber<Integer>() {
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
        };

        /**
         * Size changes of the registration processor should trigger state evaluation, as it may generate eviction permits
         */
        this.registrationProcessor.sizeObservable()
                .retryWhen(new RetryStrategyFunc(30, TimeUnit.SECONDS))
                .subscribe(sizeSubscriber);

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
        sizeSubscriber.unsubscribe();
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
                evictionStateRef.set(new EvictionState(registrationProcessor.size()));
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
        private final int steadyStateRegistrySize;

        EvictionState(int steadyStateRegistrySize) {
            this.steadyStateRegistrySize = steadyStateRegistrySize;
        }

        public boolean isEvictionAllowed() {
            int potentialSize = registrationProcessor.size() - 1;
            return potentialSize * 100.0 / steadyStateRegistrySize >= (100.0 - config.getEvictionAllowedPercentageDrop());
        }
    }
}

package com.netflix.eureka2.server.registry;

import java.util.concurrent.Semaphore;

import com.netflix.eureka2.registry.EurekaRegistrationProcessorStub;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Producer;
import rx.Subscriber;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;

/**
 * @author Tomasz Bak
 */
public class PreservableRegistryProcessorTest {

    private static final InstanceInfo FIRST_INSTANCE_INFO = SampleInstanceInfo.WebServer.build();
    private static final Source SOURCE = new Source(Origin.LOCAL, "connection#1");

    private final EurekaRegistrationProcessorStub registrationDelegate = new EurekaRegistrationProcessorStub();

    private final EvictionQuota evictionQuota = new EvictionQuota();

    private final PreservableRegistryProcessor registry = new PreservableRegistryProcessor(
            registrationDelegate, evictionQuota, registryMetrics());

    private final PublishSubject<InstanceInfo> registrationSubject = PublishSubject.create();

    @Before
    public void setUp() throws Exception {
        registry.register(FIRST_INSTANCE_INFO.getId(), registrationSubject, SOURCE).subscribe();
    }

    @Test
    public void testRegisterWithOnCompleteTerminatesRegistration() throws Exception {
        // First registration
        registrationSubject.onNext(FIRST_INSTANCE_INFO);
        registrationDelegate.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // Second registrations
        InstanceInfo update = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.DOWN).build();
        registrationSubject.onNext(update);
        registrationDelegate.verifyRegisteredWith(update);

        // Now onComplete
        registrationSubject.onCompleted();
        registrationDelegate.verifyRegistrationCompleted();
    }

    @Test
    public void testRegisterWithOnErrorPutsRegistrationOnTheEvictionQueue() throws Exception {
        // First registration
        registrationSubject.onNext(FIRST_INSTANCE_INFO);
        registrationDelegate.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // OnError registration to put it into the eviction queue
        registrationSubject.onError(new Exception("simulated registration error"));
        registrationDelegate.verifyRegistrationActive();

        // Now grant permission to evict one item
        evictionQuota.grant(1);
        registrationDelegate.verifyRegistrationCompleted();
    }

    static class EvictionQuota implements EvictionQuotaKeeper {

        private final Producer quotaProducer;
        private final Subject<Long, Long> quotaSubject = new SerializedSubject<>(PublishSubject.<Long>create());

        private final Semaphore requests = new Semaphore(0);
        private final Semaphore grants = new Semaphore(0);

        EvictionQuota() {
            quotaProducer = new Producer() {
                @Override
                public void request(long n) {
                    long left = n;
                    while (left > 0 && grants.tryAcquire()) {
                        left--;
                        quotaSubject.onNext(1L);
                    }
                    if (left > 0) {
                        requests.release((int) left);
                    }
                }
            };
        }

        void grant(long count) {
            long leftGrant = count;
            while (leftGrant > 0 && requests.tryAcquire()) {
                leftGrant--;
                quotaSubject.onNext(1L);
            }
            if (leftGrant > 0) {
                grants.release((int) count);
            }
        }

        @Override
        public Observable<Long> quota() {
            return Observable.create(new OnSubscribe<Long>() {
                @Override
                public void call(Subscriber<? super Long> subscriber) {
                    subscriber.setProducer(quotaProducer);
                    quotaSubject.subscribe(subscriber);
                }
            });
        }
    }
}
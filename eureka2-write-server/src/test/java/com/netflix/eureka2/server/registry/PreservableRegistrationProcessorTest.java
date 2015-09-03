package com.netflix.eureka2.server.registry;

import java.util.concurrent.Semaphore;

import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.registry.ChangeNotificationObservable;
import com.netflix.eureka2.registry.EurekaRegistryRegistrationStub;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Builder;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.spy;

/**
 * TODO lots of tests
 *
 * @author Tomasz Bak
 */
public class PreservableRegistrationProcessorTest {

    private static final InstanceInfo FIRST_INSTANCE_INFO = SampleInstanceInfo.WebServer.build();

    private final EurekaRegistryRegistrationStub registryStub = new EurekaRegistryRegistrationStub();

    private final ChangeNotificationObservable dataStream = ChangeNotificationObservable.create();

    private PreservableRegistrationProcessor.QuotaSubscriber quotaSubscriber;
    private PreservableRegistrationProcessor registrationProcessor;
    private TestEvictionQuotaKeeper testEvictionQuotaKeeper;

    @Before
    public void setUp() throws Exception {
        quotaSubscriber = spy(new PreservableRegistrationProcessor.QuotaSubscriber());
        registrationProcessor = new PreservableRegistrationProcessor(
                registryStub, new BasicEurekaRegistryConfig.Builder().build(), quotaSubscriber);
        testEvictionQuotaKeeper = new TestEvictionQuotaKeeper();
        registrationProcessor.setEvictionQuotaKeeper(testEvictionQuotaKeeper);
        registrationProcessor.init();
    }

    @Test
    public void testRegistrationLifecycle() throws Exception {
        String id = FIRST_INSTANCE_INFO.getId();
        Source source = new Source(Origin.LOCAL, id);
        registrationProcessor.connect(FIRST_INSTANCE_INFO.getId(), source, dataStream).subscribe();

        // First registration
        dataStream.register(FIRST_INSTANCE_INFO);
        registryStub.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // Second registrations
        InstanceInfo update = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.DOWN).build();
        dataStream.register(update);
        registryStub.verifyRegisteredWith(update);

        dataStream.unregister(update);
        registryStub.verifyUnregistered(update.getId());
    }

    @Test
    public void testRegisterWithOnCompleteTerminatesRegistration() throws Exception {
        String id = FIRST_INSTANCE_INFO.getId();
        Source source = new Source(Origin.LOCAL, id);
        registrationProcessor.connect(FIRST_INSTANCE_INFO.getId(), source, dataStream).subscribe();

        // First registration
        dataStream.register(FIRST_INSTANCE_INFO);
        registryStub.verifyRegisteredWith(FIRST_INSTANCE_INFO);

        // Second registrations
        InstanceInfo update = new Builder().withInstanceInfo(FIRST_INSTANCE_INFO).withStatus(Status.DOWN).build();
        dataStream.register(update);
        registryStub.verifyRegisteredWith(update);

        // Now onComplete
        dataStream.onCompleted();
        registryStub.verifyUnregistered(update.getId());
    }

    @Test
    public void testRegisterWithOnErrorPutsRegistrationOnTheEvictionQueue() throws Exception {
        String id = FIRST_INSTANCE_INFO.getId();
        Source source = new Source(Origin.LOCAL, id);
        registrationProcessor.connect(FIRST_INSTANCE_INFO.getId(), source, dataStream).subscribe();

        // First registration
        dataStream.register(FIRST_INSTANCE_INFO);
        registryStub.verifyRegisteredWith(FIRST_INSTANCE_INFO);
        assertThat(quotaSubscriber.registrationsToEvict.size(), is(0));

        // OnError registration to put it into the eviction queue
        dataStream.onError(new Exception("simulated registration error"));
        assertThat(quotaSubscriber.registrationsToEvict.size(), is(1));
        PreservableRegistrationProcessor.Registrant registrant = quotaSubscriber.registrationsToEvict.peek();
        assertThat(registrant.source.getName(), is(FIRST_INSTANCE_INFO.getId()));

        // Now grant permission to evict one item
        testEvictionQuotaKeeper.grant(1);
        assertThat(quotaSubscriber.registrationsToEvict.size(), is(0));
    }


    static class TestEvictionQuotaKeeper implements EvictionQuotaKeeper {

        private final Producer quotaProducer;
        private final Subject<Long, Long> quotaSubject = new SerializedSubject<>(PublishSubject.<Long>create());

        private final Semaphore requests = new Semaphore(0);
        private final Semaphore grants = new Semaphore(0);

        TestEvictionQuotaKeeper() {
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
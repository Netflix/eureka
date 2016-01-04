package com.netflix.eureka2.eureka1.rest.registry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class Eureka1RegistryProxyImplTest {

    private static final InstanceInfo V2_SAMPLE_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final com.netflix.appinfo.InstanceInfo V1_SAMPLE_INSTANCE = toEureka1xInstanceInfo(V2_SAMPLE_INSTANCE);

    private final TestScheduler testScheduler = Schedulers.test();

    private final EurekaRegistrationClientStub registrationClient = new EurekaRegistrationClientStub();

    private final Eureka1RegistryProxyImpl registryProxy = new Eureka1RegistryProxyImpl(registrationClient, testScheduler);

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testEureka1xRegistrationRequestEstablishesEureka2xClientConnection() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));
        assertThat(toEureka1xInstanceInfo(registrationClient.getRegistrationUpdates().get(0)), is(equalTo(V1_SAMPLE_INSTANCE)));
    }

    @Test
    public void testMetaDataAreAppendedToExistingRegistration() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        Map<String, String> meta = new HashMap<>();
        meta.put("keyA", "valueA");
        registryProxy.appendMeta(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId(), meta);

        InstanceInfo v2InstanceWithMeta = InstanceModel.getDefaultModel().newInstanceInfo()
                .withInstanceInfo(V2_SAMPLE_INSTANCE)
                .withMetaData(meta)
                .build();
        com.netflix.appinfo.InstanceInfo v1InstanceWithMeta = toEureka1xInstanceInfo(v2InstanceWithMeta);

        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));
        assertThat(toEureka1xInstanceInfo(registrationClient.getRegistrationUpdates().get(0)), is(equalTo(v1InstanceWithMeta)));
    }

    @Test
    public void testLeaseExpiryClosesEureka2xRegistrationChannel() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));

        // Expire the lease
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() * 1000, TimeUnit.MILLISECONDS);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(0)));
    }

    @Test
    public void testUnregisterClosesEureka2xRegistrationChannel() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));

        registryProxy.unregister(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(0)));
    }

    @Test
    public void testRenewLeaseShiftsExpiryTime() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));

        // Advance time just before expiry, and than renew the lease
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() - 1, TimeUnit.SECONDS);
        registryProxy.renewLease(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());

        // We have got extra lease time
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() - 1, TimeUnit.SECONDS);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));

        // Now we cross it
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(0)));
    }

    @Test
    public void testShutdownClosesOpenRegistrations() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(1)));

        registryProxy.shutdown();
        assertThat(registrationClient.getPendingRegistrations(), is(equalTo(0)));
    }

    static class EurekaRegistrationClientStub implements EurekaRegistrationClient {

        private final List<InstanceInfo> registrationUpdates = new CopyOnWriteArrayList<>();
        private volatile int pendingRegistrations;

        @Override
        public Observable<RegistrationStatus> register(Observable<InstanceInfo> registrant) {
            return Observable.create(subscriber -> {
                PublishSubject<RegistrationStatus> replySubject = PublishSubject.create();

                pendingRegistrations++;
                registrant
                        .doOnUnsubscribe(() -> pendingRegistrations--)
                        .subscribe(
                                next -> {
                                    replySubject.onNext(RegistrationStatus.Registered);
                                    registrationUpdates.add(next);
                                },
                                e -> replySubject.onError(e),
                                () -> replySubject.onCompleted()
                        );
            });
        }

        @Override
        public void shutdown() {
        }

        public int getPendingRegistrations() {
            return pendingRegistrations;
        }

        public List<InstanceInfo> getRegistrationUpdates() {
            return registrationUpdates;
        }
    }
}
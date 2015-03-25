package com.netflix.eureka2.eureka1.rest.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.eureka1.rest.model.Eureka1ModelConverters.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TODO Eureka client API is difficult to mock and hence the tests here have weak assertions.
 *
 * @author Tomasz Bak
 */
public class Eureka1RegistryProxyImplTest {

    private static final InstanceInfo V2_SAMPLE_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final com.netflix.appinfo.InstanceInfo V1_SAMPLE_INSTANCE = toEureka1xInstanceInfo(V2_SAMPLE_INSTANCE);

    private final TestScheduler testScheduler = Schedulers.test();

    private final EurekaRegistrationClient registrationClient = mock(EurekaRegistrationClient.class);
    private final RegistrationObservable registrationResult = TestableRegistrationObservable.create();

    private final Eureka1RegistryProxyImpl registryProxy = new Eureka1RegistryProxyImpl(registrationClient, testScheduler);

    @Before
    public void setUp() throws Exception {
        when(registrationClient.register(any(Observable.class))).thenReturn(registrationResult);
    }

    @Test
    public void testEureka1xRegistrationRequestEstablishesEureka2xClientConnection() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        verify(registrationClient, times(1)).register(any(Observable.class));
    }

    @Test
    public void testMetaDataAreAppendedToExistingRegistration() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        Map<String, String> meta = new HashMap<>();
        meta.put("keyA", "valueA");
        registryProxy.appendMeta(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId(), meta);

        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(false));
    }

    @Test
    public void testLeaseExpiryClosesEureka2xRegistrationChannel() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(false));

        // Expire the lease
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() * 1000, TimeUnit.MILLISECONDS);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(true));
    }

    @Test
    public void testUnregisterClosesEureka2xRegistrationChannel() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(false));

        registryProxy.unregister(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(true));
    }

    @Test
    public void testRenewLeaseShiftsExpiryTime() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(false));

        // Advance time just before expiry, and than renew the lease
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() - 1, TimeUnit.SECONDS);
        registryProxy.renewLease(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());

        // We have got extra lease time
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() - 1, TimeUnit.SECONDS);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(false));

        // Now we cross it
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(true));
    }

    @Test
    public void testShutdownClosesOpenRegistrations() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(false));

        registryProxy.shutdown();
        assertThat(TestableRegistrationObservable.lifecycleSubscriber.isUnsubscribed(), is(true));
    }

}
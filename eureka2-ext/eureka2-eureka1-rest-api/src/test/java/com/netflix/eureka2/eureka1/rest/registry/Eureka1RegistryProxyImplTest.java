package com.netflix.eureka2.eureka1.rest.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.stubs.EurekaRegistrationClientStub;
import org.junit.Test;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;
import static com.netflix.eureka2.registry.instance.InstanceInfo.anInstanceInfo;
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

    @Test
    public void testEureka1xRegistrationRequestEstablishesEureka2xClientConnection() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.getPendingRegistrations().size(), is(equalTo(1)));
        assertThat(toEureka1xInstanceInfo(registrationClient.getLastRegistrationUpdate()), is(equalTo(V1_SAMPLE_INSTANCE)));
    }

    @Test
    public void testMetaDataAreAppendedToExistingRegistration() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        Map<String, String> meta = new HashMap<>();
        meta.put("keyA", "valueA");
        registryProxy.appendMeta(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId(), meta);

        InstanceInfo v2InstanceWithMeta = anInstanceInfo()
                .withInstanceInfo(V2_SAMPLE_INSTANCE)
                .withMetaData(meta)
                .build();
        com.netflix.appinfo.InstanceInfo v1InstanceWithMeta = toEureka1xInstanceInfo(v2InstanceWithMeta);

        assertThat(registrationClient.getPendingRegistrations().size(), is(equalTo(1)));
        assertThat(toEureka1xInstanceInfo(registrationClient.getLastRegistrationUpdate()), is(equalTo(v1InstanceWithMeta)));
    }

    @Test
    public void testLeaseExpiryClosesEureka2xRegistrationChannel() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(true));

        // Expire the lease
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() * 1000, TimeUnit.MILLISECONDS);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(false));
    }

    @Test
    public void testUnregisterClosesEureka2xRegistrationChannel() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(true));

        registryProxy.unregister(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());
        assertThat(registrationClient.hasSubscribedRegistrations(), is(false));
    }

    @Test
    public void testRenewLeaseShiftsExpiryTime() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(true));

        // Advance time just before expiry, and than renew the lease
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() - 1, TimeUnit.SECONDS);
        registryProxy.renewLease(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());

        // We have got extra lease time
        testScheduler.advanceTimeBy(V1_SAMPLE_INSTANCE.getLeaseInfo().getDurationInSecs() - 1, TimeUnit.SECONDS);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(true));

        // Now we cross it
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(false));
    }

    @Test
    public void testShutdownClosesOpenRegistrations() throws Exception {
        registryProxy.register(V1_SAMPLE_INSTANCE);
        assertThat(registrationClient.hasSubscribedRegistrations(), is(true));

        registryProxy.shutdown();
        assertThat(registrationClient.hasSubscribedRegistrations(), is(false));
    }
}
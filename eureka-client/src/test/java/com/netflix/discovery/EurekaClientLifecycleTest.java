package com.netflix.discovery;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.PropertiesInstanceConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static com.netflix.discovery.util.EurekaEntityFunctions.countInstances;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EurekaClientLifecycleTest {

    private static final String MY_APPLICATION_NAME = "MYAPPLICATION";
    private static final String MY_INSTANCE_ID = "myInstanceId";

    private static final Applications APPLICATIONS = InstanceInfoGenerator.newBuilder(1, 1).build().toApplications();
    private static final Applications APPLICATIONS_DELTA = new Applications(APPLICATIONS.getAppsHashCode(), 1L, Collections.<Application>emptyList());

    public static final int TIMEOUT_MS = 2 * 1000;

    public static final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);

    public static SimpleEurekaHttpServer eurekaHttpServer;

    @BeforeClass
    public static void setupClass() throws IOException {
        eurekaHttpServer = new SimpleEurekaHttpServer(requestHandler);
        when(requestHandler.register(any(InstanceInfo.class))).thenReturn(EurekaHttpResponse.status(204));
        when(requestHandler.cancel(MY_APPLICATION_NAME, MY_INSTANCE_ID)).thenReturn(EurekaHttpResponse.status(200));
        when(requestHandler.sendHeartBeat(MY_APPLICATION_NAME, MY_INSTANCE_ID, null, null)).thenReturn(
                anEurekaHttpResponse(200, InstanceInfo.class).build()
        );
        when(requestHandler.getApplications()).thenReturn(
                anEurekaHttpResponse(200, APPLICATIONS).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        when(requestHandler.getDelta()).thenReturn(
                anEurekaHttpResponse(200, APPLICATIONS_DELTA).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
    }

    @AfterClass
    public static void tearDownClass() {
        if (eurekaHttpServer != null) {
            eurekaHttpServer.shutdown();
        }
    }

    @Test
    public void testEurekaClientLifecycle() throws Exception {
        Injector injector = LifecycleInjector.builder()
                .withModules(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).to(LocalEurekaInstanceConfig.class);
                                bind(EurekaClientConfig.class).to(LocalEurekaClientConfig.class);
                                bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
                            }
                        }
                )
                .build().createInjector();
        LifecycleManager lifecycleManager = injector.getInstance(LifecycleManager.class);
        lifecycleManager.start();

        EurekaClient client = injector.getInstance(EurekaClient.class);

        // Check registration
        verify(requestHandler, timeout(TIMEOUT_MS).atLeast(1)).register(any(InstanceInfo.class));

        // Check registry fetch
        verify(requestHandler, timeout(TIMEOUT_MS).times(1)).getApplications();
        verify(requestHandler, timeout(TIMEOUT_MS).atLeast(1)).getDelta();
        assertThat(countInstances(client.getApplications()), is(equalTo(1)));

        // Shutdown container, and check that unregister happens
        lifecycleManager.close();
        verify(requestHandler, times(1)).cancel(MY_APPLICATION_NAME, MY_INSTANCE_ID);
    }

    @Test
    public void testBackupRegistryInjection() throws Exception {
        final BackupRegistry backupRegistry = mock(BackupRegistry.class);
        when(backupRegistry.fetchRegistry()).thenReturn(APPLICATIONS);

        Injector injector = LifecycleInjector.builder()
                .withModules(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).to(LocalEurekaInstanceConfig.class);
                                bind(EurekaClientConfig.class).to(BadServerEurekaClientConfig1.class);
                                bind(BackupRegistry.class).toInstance(backupRegistry);
                                bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
                            }
                        }
                )
                .build().createInjector();
        LifecycleManager lifecycleManager = injector.getInstance(LifecycleManager.class);
        lifecycleManager.start();

        EurekaClient client = injector.getInstance(EurekaClient.class);
        verify(backupRegistry, atLeast(1)).fetchRegistry();
        assertThat(countInstances(client.getApplications()), is(equalTo(1)));
    }

    @Test(expected = ProvisionException.class)
    public void testEnforcingRegistrationOnInitFastFail() {
        Injector injector = LifecycleInjector.builder()
                .withModules(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).to(LocalEurekaInstanceConfig.class);
                                bind(EurekaClientConfig.class).to(BadServerEurekaClientConfig2.class);
                                bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
                            }
                        }
                )
                .build().createInjector();
        LifecycleManager lifecycleManager = injector.getInstance(LifecycleManager.class);
        try {
            lifecycleManager.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // this will throw a Guice ProvisionException for the constructor failure
        EurekaClient client = injector.getInstance(EurekaClient.class);
    }

    private static class LocalEurekaInstanceConfig extends PropertiesInstanceConfig {

        @Override
        public String getInstanceId() {
            return MY_INSTANCE_ID;
        }

        @Override
        public String getAppname() {
            return MY_APPLICATION_NAME;
        }

        @Override
        public int getLeaseRenewalIntervalInSeconds() {
            return 1;
        }
    }

    private static class LocalEurekaClientConfig extends DefaultEurekaClientConfig {
        @Override
        public List<String> getEurekaServerServiceUrls(String myZone) {
            return singletonList(eurekaHttpServer.getServiceURI().toString());
        }

        @Override
        public int getInitialInstanceInfoReplicationIntervalSeconds() {
            return 0;
        }

        @Override
        public int getInstanceInfoReplicationIntervalSeconds() {
            return 1;
        }

        @Override
        public int getRegistryFetchIntervalSeconds() {
            return 1;
        }
    }

    private static class BadServerEurekaClientConfig1 extends LocalEurekaClientConfig {
        @Override
        public List<String> getEurekaServerServiceUrls(String myZone) {
            return singletonList("http://localhost:1/v2/"); // Fail fast on bad port number
        }

        @Override
        public boolean shouldRegisterWithEureka() {
            return false;
        }
    }

    private static class BadServerEurekaClientConfig2 extends LocalEurekaClientConfig {
        @Override
        public List<String> getEurekaServerServiceUrls(String myZone) {
            return singletonList("http://localhost:1/v2/"); // Fail fast on bad port number
        }

        @Override
        public boolean shouldFetchRegistry() {
            return false;
        }

        @Override
        public boolean shouldEnforceRegistrationAtInit() {
            return true;
        }
    }
}

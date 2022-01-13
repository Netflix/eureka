package com.netflix.discovery;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.PropertiesInstanceConfig;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.EndpointRandomizer;
import com.netflix.discovery.shared.resolver.ResolverUtils;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.governator.guice.LifecycleInjector;
import com.netflix.governator.lifecycle.LifecycleManager;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the `shouldEnforceFetchRegistryAtInit` configuration property, which throws an exception during `DiscoveryClient`
 * construction if set to `true` and both the primary and backup registry fail to return a successful response.
 */
public class EurekaClientLifecycleServerFailureTest {

    private static final String MY_APPLICATION_NAME = "MYAPPLICATION";
    private static final String MY_INSTANCE_ID = "myInstanceId";

    private static final Applications APPLICATIONS = InstanceInfoGenerator.newBuilder(1, 1).build().toApplications();
    private static final Applications APPLICATIONS_DELTA = new Applications(APPLICATIONS.getAppsHashCode(), 1L, Collections.emptyList());

    public static final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);

    public static SimpleEurekaHttpServer eurekaHttpServer;

    @BeforeClass
    public static void setupClass() throws IOException {
        eurekaHttpServer = new SimpleEurekaHttpServer(requestHandler);
        when(requestHandler.getApplications()).thenReturn(
                anEurekaHttpResponse(500, APPLICATIONS).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
        when(requestHandler.getDelta()).thenReturn(
                anEurekaHttpResponse(500, APPLICATIONS_DELTA).type(MediaType.APPLICATION_JSON_TYPE).build()
        );
    }

    @AfterClass
    public static void tearDownClass() {
        if (eurekaHttpServer != null) {
            eurekaHttpServer.shutdown();
        }
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

    /**
     * EurekaClientConfig configured to enforce fetch registry at init
     */
    private static class LocalEurekaClientConfig1 extends DefaultEurekaClientConfig {
        @Override
        public List<String> getEurekaServerServiceUrls(String myZone) {
            return singletonList(eurekaHttpServer.getServiceURI().toString());
        }

        @Override
        public boolean shouldEnforceFetchRegistryAtInit() {
            return true;
        }
    }

    /**
     * EurekaClientConfig configured to enforce fetch registry at init but not to fetch registry
     */
    private static class LocalEurekaClientConfig2 extends DefaultEurekaClientConfig {
        @Override
        public List<String> getEurekaServerServiceUrls(String myZone) {
            return singletonList(eurekaHttpServer.getServiceURI().toString());
        }

        @Override
        public boolean shouldEnforceFetchRegistryAtInit() {
            return true;
        }

        @Override
        public boolean shouldFetchRegistry() {
            return false;
        }
    }

    /**
     * n.b. without a configured backup registry, the default backup registry is set to  `NotImplementedRegistryImpl`,
     * which returns `null` for its list of applications and thus results in a failure to return a successful response
     * for registry data when used.
     */
    @Test(expected = ProvisionException.class)
    public void testEnforceFetchRegistryAtInitPrimaryAndBackupFailure() {
        Injector injector = LifecycleInjector.builder()
                .withModules(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).to(LocalEurekaInstanceConfig.class);
                                bind(EurekaClientConfig.class).to(LocalEurekaClientConfig1.class);
                                bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
                                bind(EndpointRandomizer.class).toInstance(ResolverUtils::randomize);
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
        injector.getInstance(EurekaClient.class);
    }

    @Test
    public void testEnforceFetchRegistryAtInitPrimaryFailureAndBackupSuccess() {
        Injector injector = LifecycleInjector.builder()
                .withModules(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).to(LocalEurekaInstanceConfig.class);
                                bind(EurekaClientConfig.class).to(LocalEurekaClientConfig1.class);
                                bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
                                bind(EndpointRandomizer.class).toInstance(ResolverUtils::randomize);
                                bind(BackupRegistry.class).toInstance(new MockBackupRegistry()); // returns empty list on registry fetch
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

        // this will not throw a Guice ProvisionException for the constructor failure
        EurekaClient client = injector.getInstance(EurekaClient.class);
        Assert.assertNotNull(client);
    }

    @Test
    public void testEnforceFetchRegistryAtInitPrimaryFailureNoop() {
        Injector injector = LifecycleInjector.builder()
                .withModules(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).to(LocalEurekaInstanceConfig.class);
                                bind(EurekaClientConfig.class).to(LocalEurekaClientConfig2.class);
                                bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
                                bind(EndpointRandomizer.class).toInstance(ResolverUtils::randomize);
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

        // this will not throw a Guice ProvisionException for the constructor failure
        EurekaClient client = injector.getInstance(EurekaClient.class);
        Assert.assertNotNull(client);
    }

}
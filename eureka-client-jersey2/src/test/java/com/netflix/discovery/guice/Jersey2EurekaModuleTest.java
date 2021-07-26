package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import com.netflix.discovery.shared.transport.jersey2.Jersey2TransportClientFactories;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.LifecycleInjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author David Liu
 */
public class Jersey2EurekaModuleTest {

    private LifecycleInjector injector;

    @BeforeEach
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("eureka.region", "default");
        ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default", "http://localhost:8080/eureka/v2");

        injector = InjectorBuilder
                .fromModule(new Jersey2EurekaModule())
                .overrideWith(new AbstractModule() {
                    @Override
                    protected void configure() {
                        // the default impl of EurekaInstanceConfig is CloudInstanceConfig, which we only want in an AWS
                        // environment. Here we override that by binding MyDataCenterInstanceConfig to EurekaInstanceConfig.
                        bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);
                    }
                })
                .createInjector();
    }

    @AfterEach
    public void tearDown() {
        if (injector != null) {
            injector.shutdown();
        }
        ConfigurationManager.getConfigInstance().clear();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDI() {
        InstanceInfo instanceInfo = injector.getInstance(InstanceInfo.class);
        Assertions.assertEquals(ApplicationInfoManager.getInstance().getInfo(), instanceInfo);

        EurekaClient eurekaClient = injector.getInstance(EurekaClient.class);
        DiscoveryClient discoveryClient = injector.getInstance(DiscoveryClient.class);

        Assertions.assertEquals(DiscoveryManager.getInstance().getEurekaClient(), eurekaClient);
        Assertions.assertEquals(DiscoveryManager.getInstance().getDiscoveryClient(), discoveryClient);
        Assertions.assertEquals(eurekaClient, discoveryClient);

        EurekaClientConfig eurekaClientConfig = injector.getInstance(EurekaClientConfig.class);
        Assertions.assertEquals(DiscoveryManager.getInstance().getEurekaClientConfig(), eurekaClientConfig);

        EurekaInstanceConfig eurekaInstanceConfig = injector.getInstance(EurekaInstanceConfig.class);
        Assertions.assertEquals(DiscoveryManager.getInstance().getEurekaInstanceConfig(), eurekaInstanceConfig);

        Binding<TransportClientFactories> binding = injector.getExistingBinding(Key.get(TransportClientFactories.class));
        Assertions.assertNotNull(binding);  // has a binding for jersey2

        TransportClientFactories transportClientFactories = injector.getInstance(TransportClientFactories.class);
        Assertions.assertTrue(transportClientFactories instanceof Jersey2TransportClientFactories);
    }
}

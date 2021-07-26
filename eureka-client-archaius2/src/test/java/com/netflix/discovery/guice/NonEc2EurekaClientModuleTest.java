package com.netflix.discovery.guice;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.Archaius2VipAddressResolver;
import com.netflix.appinfo.providers.VipAddressResolver;
import com.netflix.archaius.config.MapConfig;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.LifecycleInjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author David Liu
 */
public class NonEc2EurekaClientModuleTest {

    private LifecycleInjector injector;

    @BeforeEach
    public void setUp() throws Exception {
        injector = InjectorBuilder
                .fromModules(
                        new ArchaiusModule() {
                            @Override
                            protected void configureArchaius() {
                                bindApplicationConfigurationOverride().toInstance(
                                        MapConfig.builder()
                                                .put("eureka.region", "default")
                                                .put("eureka.shouldFetchRegistry", "false")
                                                .put("eureka.registration.enabled", "false")
                                                .put("eureka.serviceUrl.default", "http://localhost:8080/eureka/v2")
                                                .put("eureka.shouldInitAsEc2", "false")
                                                .put("eureka.instanceDeploymentEnvironment", "non-ec2")
                                                .build()
                                );
                            }
                        },
                        new EurekaClientModule()
                )
                .createInjector();
    }

    @AfterEach
    public void tearDown() {
        if (injector != null) {
            injector.shutdown();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDI() {
        InstanceInfo instanceInfo = injector.getInstance(InstanceInfo.class);
        Assertions.assertEquals(ApplicationInfoManager.getInstance().getInfo(), instanceInfo);

        VipAddressResolver vipAddressResolver = injector.getInstance(VipAddressResolver.class);
        Assertions.assertTrue(vipAddressResolver instanceof Archaius2VipAddressResolver);

        EurekaClient eurekaClient = injector.getInstance(EurekaClient.class);
        DiscoveryClient discoveryClient = injector.getInstance(DiscoveryClient.class);

        Assertions.assertEquals(DiscoveryManager.getInstance().getEurekaClient(), eurekaClient);
        Assertions.assertEquals(DiscoveryManager.getInstance().getDiscoveryClient(), discoveryClient);
        Assertions.assertEquals(eurekaClient, discoveryClient);

        EurekaClientConfig eurekaClientConfig = injector.getInstance(EurekaClientConfig.class);
        Assertions.assertEquals(DiscoveryManager.getInstance().getEurekaClientConfig(), eurekaClientConfig);

        EurekaInstanceConfig eurekaInstanceConfig = injector.getInstance(EurekaInstanceConfig.class);
        Assertions.assertEquals(DiscoveryManager.getInstance().getEurekaInstanceConfig(), eurekaInstanceConfig);
        Assertions.assertTrue(eurekaInstanceConfig instanceof EurekaArchaius2InstanceConfig);

        ApplicationInfoManager applicationInfoManager = injector.getInstance(ApplicationInfoManager.class);
        InstanceInfo myInfo = applicationInfoManager.getInfo();
        Assertions.assertEquals(DataCenterInfo.Name.MyOwn, myInfo.getDataCenterInfo().getName());
    }
}

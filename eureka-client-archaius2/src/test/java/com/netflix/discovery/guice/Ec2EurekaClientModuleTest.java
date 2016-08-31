package com.netflix.discovery.guice;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author David Liu
 */
public class Ec2EurekaClientModuleTest {

    private LifecycleInjector injector;

    @Before
    public void setUp() throws Exception {
        injector = InjectorBuilder
                .fromModules(
                        new ArchaiusModule() {
                            @Override
                            protected void configureArchaius() {
                                bindApplicationConfigurationOverride().toInstance(
                                        MapConfig.builder()
                                                .put("eureka.new.region", "default")
                                                .put("eureka.new.shouldFetchRegistry", "false")
                                                .put("eureka.new.registration.enabled", "false")
                                                .put("eureka.new.serviceUrl.default", "http://localhost:8080/eureka/v2")
                                                .put("eureka.new.vipAddress", "some-thing")
                                                .put("eureka.new.validateInstanceId", "false")
                                                .put("eureka.new.mt.num_retries", 0)
                                                .put("eureka.new.mt.connect_timeout", 1000)
                                                .put("eureka.new.shouldInitAsEc2", true)
                                                .build()
                                );
                            }
                        },
                        new EurekaClientModule() {
                            @Override
                            protected void configureEureka() {
                                bindEurekaInstanceConfigNamespace().toInstance("eureka.new");
                                bindEurekaClientConfigNamespace().toInstance("eureka.new");
                            }
                        }
                )
                .createInjector();
    }

    @After
    public void tearDown() {
        if (injector != null) {
            injector.shutdown();
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDI() {
        InstanceInfo instanceInfo = injector.getInstance(InstanceInfo.class);
        Assert.assertEquals(ApplicationInfoManager.getInstance().getInfo(), instanceInfo);

        VipAddressResolver vipAddressResolver = injector.getInstance(VipAddressResolver.class);
        Assert.assertTrue(vipAddressResolver instanceof Archaius2VipAddressResolver);

        EurekaClient eurekaClient = injector.getInstance(EurekaClient.class);
        DiscoveryClient discoveryClient = injector.getInstance(DiscoveryClient.class);

        Assert.assertEquals(DiscoveryManager.getInstance().getEurekaClient(), eurekaClient);
        Assert.assertEquals(DiscoveryManager.getInstance().getDiscoveryClient(), discoveryClient);
        Assert.assertEquals(eurekaClient, discoveryClient);

        EurekaClientConfig eurekaClientConfig = injector.getInstance(EurekaClientConfig.class);
        Assert.assertEquals(DiscoveryManager.getInstance().getEurekaClientConfig(), eurekaClientConfig);

        EurekaInstanceConfig eurekaInstanceConfig = injector.getInstance(EurekaInstanceConfig.class);
        Assert.assertEquals(DiscoveryManager.getInstance().getEurekaInstanceConfig(), eurekaInstanceConfig);
        Assert.assertTrue(eurekaInstanceConfig instanceof Ec2EurekaArchaius2InstanceConfig);

        ApplicationInfoManager applicationInfoManager = injector.getInstance(ApplicationInfoManager.class);
        InstanceInfo myInfo = applicationInfoManager.getInfo();
        Assert.assertTrue(myInfo.getDataCenterInfo() instanceof AmazonInfo);
        Assert.assertEquals(DataCenterInfo.Name.Amazon, myInfo.getDataCenterInfo().getName());
    }
}

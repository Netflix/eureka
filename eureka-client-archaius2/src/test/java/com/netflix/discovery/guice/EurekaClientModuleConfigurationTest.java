package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.EurekaInstanceInfoFactory;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.EurekaInstanceConfigFactory;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.LifecycleInjector;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author David Liu
 */
public class EurekaClientModuleConfigurationTest {

    @Test
    public void testBindEurekaInstanceConfigFactory() {
        final EurekaInstanceConfigFactory mockInstanceConfigFactory = Mockito.mock(EurekaInstanceConfigFactory.class);
        final EurekaInstanceConfig mockConfig = Mockito.mock(EurekaInstanceConfig.class);
        final ApplicationInfoManager mockInfoManager = Mockito.mock(ApplicationInfoManager.class);
        final EurekaInstanceInfoFactory mockInstanceInfoFactory = Mockito.mock(EurekaInstanceInfoFactory.class);
        final InstanceInfo mockInstanceInfo = Mockito.mock(InstanceInfo.class);

        Mockito.when(mockInstanceConfigFactory.get()).thenReturn(mockConfig);
        Mockito.when(mockInstanceInfoFactory.get()).thenReturn(mockInstanceInfo);

        LifecycleInjector injector = InjectorBuilder
                .fromModules(
                        new ArchaiusModule(),
                        new EurekaClientModule() {
                            @Override
                            protected void configureEureka() {
                                bindEurekaInstanceConfigFactory().toInstance(mockInstanceConfigFactory);
                            }
                        },
                        new EurekaClientModule() {
                            @Override
                            protected void configureEureka() {
                                bindEurekaInstanceInfoFactory().toInstance(mockInstanceInfoFactory);
                            }
                        }
                )
                .overrideWith(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                // this is usually bound as an eager singleton that can trigger other parts to
                                // initialize, so do an override to a mock here to prevent that.
                                bind(ApplicationInfoManager.class).toInstance(mockInfoManager);
                            }
                        })
                .createInjector();

        EurekaInstanceConfig config = injector.getInstance(EurekaInstanceConfig.class);
        Assert.assertEquals(mockConfig, config);

        InstanceInfo instanceInfo = injector.getInstance(InstanceInfo.class);
        Assert.assertEquals(mockInstanceInfo, instanceInfo);
    }
}

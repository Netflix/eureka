package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
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
        final EurekaInstanceConfigFactory mockFactory = Mockito.mock(EurekaInstanceConfigFactory.class);
        final EurekaInstanceConfig mockConfig = Mockito.mock(EurekaInstanceConfig.class);
        final ApplicationInfoManager mockInfoManager = Mockito.mock(ApplicationInfoManager.class);

        Mockito.when(mockFactory.get()).thenReturn(mockConfig);

        LifecycleInjector injector = InjectorBuilder
                .fromModules(
                        new ArchaiusModule(),
                        new EurekaClientModule() {
                            @Override
                            protected void configureEureka() {
                                bindEurekaInstanceConfigFactory().toInstance(mockFactory);
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
    }
}

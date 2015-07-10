package com.netflix.eureka2.integration.server.startup;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.archaius.inject.ApplicationLayer;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaBridgeServerConfigurationModule;
import com.netflix.eureka2.server.EurekaBridgeServerRunner;
import com.netflix.eureka2.server.EurekaWriteServerModule;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.simulator.Eureka1ServerResource;
import netflix.adminresources.resources.KaryonWebAdminModule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Arrays;
import java.util.List;

import static com.netflix.eureka2.server.config.ServerConfigurationNames.DEFAULT_CONFIG_PREFIX;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class BridgeServerStartupAndShutdownIntegrationTest extends AbstractStartupAndShutdownIntegrationTest<EurekaBridgeServerRunner> {

    public static final String SERVER_NAME = "bridge-server-startupAndShutdown";

    @Rule
    public final Eureka1ServerResource eureka1ServerResource = new Eureka1ServerResource();

    @Test(timeout = 40000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        EurekaBridgeServerRunner bridgeServerRunner = new EurekaBridgeServerRunner(SERVER_NAME) {
            @Override
            protected List<Module> getModules() {
                Module configModule;

                if (config == null && name == null) {
                    configModule = EurekaBridgeServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX);
                } else if (config == null) {  // have name
                    configModule = Modules
                            .override(EurekaBridgeServerConfigurationModule.fromArchaius(DEFAULT_CONFIG_PREFIX))
                            .with(new AbstractModule() {
                                @Override
                                protected void configure() {
                                    bind(String.class).annotatedWith(ApplicationLayer.class).toInstance(name);
                                }
                            });
                } else {  // have config
                    configModule = EurekaBridgeServerConfigurationModule.fromConfig(config);
                }

                Module eureka1Module = Modules
                        .override(new EurekaModule())
                        .with(new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);
                                bind(DiscoveryClient.class).toInstance(eureka1ServerResource.createDiscoveryClient("bridgeService"));
                            }
                        });

                return Arrays.asList(
                        configModule,
                        new CommonEurekaServerModule(),
                        new EurekaWriteServerModule(),
                        new KaryonWebAdminModule(),
                        eureka1Module
                );
            }
        };

        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, bridgeServerRunner);
    }

}
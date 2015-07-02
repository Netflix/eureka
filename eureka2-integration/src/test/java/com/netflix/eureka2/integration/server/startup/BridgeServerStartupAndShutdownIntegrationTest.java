package com.netflix.eureka2.integration.server.startup;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaBridgeServerRunner;
import com.netflix.eureka2.simulator.Eureka1ServerResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

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
            protected Module applyEurekaOverride(Module module) {
                return Modules.override(module).with(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);
                                bind(DiscoveryClient.class).toInstance(eureka1ServerResource.createDiscoveryClient("bridgeService"));
                            }
                        }
                );
            }
        };
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, bridgeServerRunner);
    }

}
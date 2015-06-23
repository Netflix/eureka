package com.netflix.eureka2.integration.server.startup;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.util.Modules;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.providers.CloudInstanceConfigProvider;
import com.netflix.appinfo.providers.MyDataCenterInstanceConfigProvider;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.server.EurekaBridgeServer;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.simulator.Eureka1ServerResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class BridgeServerStartupAndShutdownIntegrationTest extends
        AbstractStartupAndShutdownIntegrationTest<BridgeServerConfig, EurekaBridgeServer> {

    public static final String SERVER_NAME = "bridge-server-startupAndShutdown";

    @Rule
    public final Eureka1ServerResource eureka1ServerResource = new Eureka1ServerResource();

    private EurekaBridgeServer bridgeServer;

    @Before
    public void setUpBridgeServer() throws Exception {
        bridgeServer = new EurekaBridgeServer(SERVER_NAME) {
            @Override
            protected Module getModule() {
                return Modules.override(super.getModule())
                        .with(new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(EurekaInstanceConfig.class).toProvider(MyDataCenterInstanceConfigProvider.class).in(Scopes.SINGLETON);
                                bind(DiscoveryClient.class).toInstance(eureka1ServerResource.createDiscoveryClient("bridgeService"));
                            }
                        });
            }
        };
    }

    @Test(timeout = 40000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        verifyThatStartsWithFileBasedConfiguration(SERVER_NAME, bridgeServer);
    }
}
package com.netflix.discovery;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.shared.Application;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientDisableRegistryTest {

    private static final int localRandomEurekaPort = 7799;
    private DiscoveryClient client;
    private MockRemoteEurekaServer mockLocalEurekaServer;

    @Before
    public void setUp() throws Exception {
        final int eurekaPort = localRandomEurekaPort + (int)(Math.random() * 10);
        Properties props = new Properties();
        props.setProperty("eureka.registration.enabled", "false");
        props.setProperty("eureka.shouldFetchRegistry", "false");
        props.setProperty("eureka.serviceUrl.default",
                "http://localhost:" + eurekaPort +
                MockRemoteEurekaServer.EUREKA_API_BASE_PATH);
        
        ConfigurationManager.loadProperties(props);

        mockLocalEurekaServer = new MockRemoteEurekaServer(eurekaPort, Collections.<String, Application>emptyMap(),
                                                           Collections.<String, Application>emptyMap(),
                                                           Collections.<String, Application>emptyMap(),
                                                           Collections.<String, Application>emptyMap());
        mockLocalEurekaServer.start();

        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        builder.setIPAddr("10.10.101.00");
        builder.setHostName("Hosttt");
        builder.setAppName("EurekaTestApp-" + UUID.randomUUID());
        builder.setDataCenterInfo(new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
        client = new DiscoveryClient(builder.build(), new DefaultEurekaClientConfig());
    }

    @Test
    public void testDisableFetchRegistry() throws Exception {
        Assert.assertFalse("Registry fetch disabled but eureka server recieved a registry fetch.",
                           mockLocalEurekaServer.isSentRegistry());
    }
}

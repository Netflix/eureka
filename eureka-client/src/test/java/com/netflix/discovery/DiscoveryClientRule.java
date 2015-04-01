package com.netflix.discovery;

import java.util.UUID;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule for discovery client + collection of static methods for setting it up.
 */
public class DiscoveryClientRule extends ExternalResource {

    public static final String REMOTE_REGION = "myregion";
    public static final String REMOTE_ZONE = "myzone";
    public static final int CLIENT_REFRESH_RATE = 10;

    protected int port;
    private DiscoveryClient client;

    public DiscoveryClient getClient() {
        if(client == null) {
            setupDiscoveryClientConfig(port, "/eureka/v2/");
            InstanceInfo clientInstanceInfo = newInstanceInfoBuilder(30).build();
            client = setupDiscoveryClient(clientInstanceInfo);
        }
        return client;
    }

    @Override
    protected void after() {
        if(client != null) {
            client.shutdown();
        }
        clearDiscoveryClientConfig();
    }

    public static void setupDiscoveryClientConfig(int serverPort, String path) {
        ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", "true");
        ConfigurationManager.getConfigInstance().setProperty("eureka.responseCacheAutoExpirationInSeconds", "10");
        ConfigurationManager.getConfigInstance().setProperty("eureka.client.refresh.interval", CLIENT_REFRESH_RATE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "false");
        ConfigurationManager.getConfigInstance().setProperty("eureka.fetchRemoteRegionsRegistry", REMOTE_REGION);
        ConfigurationManager.getConfigInstance().setProperty("eureka.myregion.availabilityZones", REMOTE_ZONE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.serviceUrl.default",
                "http://localhost:" + serverPort + path);
    }

    public static void clearDiscoveryClientConfig() {
        ConfigurationManager.getConfigInstance().clearProperty("eureka.client.refresh.interval");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.registration.enabled");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.fetchRemoteRegionsRegistry");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.myregion.availabilityZones");
        ConfigurationManager.getConfigInstance().clearProperty("eureka.serviceUrl.default");
    }

    public static DiscoveryClient setupDiscoveryClient(InstanceInfo clientInstanceInfo) {
        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig();
        // setup config in advance, used in initialize converter
        DiscoveryManager.getInstance().setEurekaClientConfig(config);
        DiscoveryClient client = new DiscoveryClient(clientInstanceInfo, config);
        ApplicationInfoManager.getInstance().initComponent(new MyDataCenterInstanceConfig());
        return client;
    }

    public static InstanceInfo.Builder newInstanceInfoBuilder(int renewalIntervalInSecs) {
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
        builder.setLeaseInfo(LeaseInfo.Builder.newBuilder().setRenewalIntervalInSecs(renewalIntervalInSecs).build());
        return builder;
    }
}

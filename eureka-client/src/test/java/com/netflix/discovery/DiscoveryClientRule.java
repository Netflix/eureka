package com.netflix.discovery;

import java.util.UUID;
import java.util.concurrent.Callable;

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

    private final boolean registrationEnabled;
    private final boolean registryFetchEnabled;
    private final Callable<Integer> portResolverCallable;
    private final InstanceInfo instance;

    private EurekaClient client;

    DiscoveryClientRule(DiscoveryClientRuleBuilder builder) {
        this.registrationEnabled = builder.registrationEnabled;
        this.registryFetchEnabled = builder.registryFetchEnabled;
        this.portResolverCallable = builder.portResolverCallable;
        this.instance = builder.instance;
    }

    public EurekaClient getClient() {
        if (client == null) {
            int port;
            try {
                port = portResolverCallable.call();
            } catch (Exception e) {
                throw new RuntimeException("Cannot resolve discovery server port number", e);
            }
            setupDiscoveryClientConfig(port, "/eureka/v2/");

            ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", Boolean.valueOf(registrationEnabled));
            ConfigurationManager.getConfigInstance().setProperty("eureka.shouldFetchRegistry", Boolean.valueOf(registryFetchEnabled));

            InstanceInfo clientInstanceInfo = instance == null ? newInstanceInfoBuilder(30).build() : instance;
            client = setupDiscoveryClient(clientInstanceInfo);
        }
        return client;
    }

    @Override
    protected void after() {
        if (client != null) {
            client.shutdown();
        }
        clearDiscoveryClientConfig();
    }

    public static DiscoveryClientRuleBuilder newBuilder() {
        return new DiscoveryClientRuleBuilder();
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

    public static EurekaClient setupDiscoveryClient(InstanceInfo clientInstanceInfo) {
        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig();
        // setup config in advance, used in initialize converter
        ApplicationInfoManager applicationInfoManager = new ApplicationInfoManager(new MyDataCenterInstanceConfig(), clientInstanceInfo);

        DiscoveryManager.getInstance().setEurekaClientConfig(config);
        EurekaClient client = new DiscoveryClient(applicationInfoManager, config);
        return client;
    }

    public static EurekaClient setupInjector(InstanceInfo clientInstanceInfo) {


        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig();
        // setup config in advance, used in initialize converter
        DiscoveryManager.getInstance().setEurekaClientConfig(config);
        EurekaClient client = new DiscoveryClient(clientInstanceInfo, config);
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

    public static class DiscoveryClientRuleBuilder {
        private boolean registrationEnabled;
        private boolean registryFetchEnabled;
        private Callable<Integer> portResolverCallable;
        private InstanceInfo instance;

        public DiscoveryClientRuleBuilder registration(boolean enabled) {
            this.registrationEnabled = enabled;
            return this;
        }

        public DiscoveryClientRuleBuilder registryFetch(boolean enabled) {
            this.registryFetchEnabled = enabled;
            return this;
        }

        public DiscoveryClientRuleBuilder portResolver(Callable<Integer> portResolverCallable) {
            this.portResolverCallable = portResolverCallable;
            return this;
        }

        public DiscoveryClientRuleBuilder instanceInfo(InstanceInfo instance) {
            this.instance = instance;
            return this;
        }

        public DiscoveryClientRule build() {
            return new DiscoveryClientRule(this);
        }
    }
}

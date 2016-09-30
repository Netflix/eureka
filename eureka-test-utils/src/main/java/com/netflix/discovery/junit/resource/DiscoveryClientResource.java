package com.netflix.discovery.junit.resource;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Preconditions;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs;
import com.netflix.eventbus.impl.EventBusImpl;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.InvalidSubscriberException;
import com.netflix.eventbus.spi.Subscribe;
import org.junit.rules.ExternalResource;

/**
 * JUnit rule for discovery client + collection of static methods for setting it up.
 */
public class DiscoveryClientResource extends ExternalResource {

    public static final String REMOTE_REGION = "myregion";
    public static final String REMOTE_ZONE = "myzone";
    public static final int CLIENT_REFRESH_RATE = 10;
    public static final String EUREKA_TEST_NAMESPACE = "eurekaTestNamespace.";

    private static final Set<String> SYSTEM_PROPERTY_TRACKER = new HashSet<>();

    private final boolean registrationEnabled;
    private final boolean registryFetchEnabled;
    private final InstanceInfo instance;

    private final SimpleEurekaHttpServer eurekaHttpServer;
    private final Callable<Integer> portResolverCallable;
    private final List<String> remoteRegions;
    private final String vipFetch;
    private final String userName;
    private final String password;

    private EventBus eventBus;
    private ApplicationInfoManager applicationManager;
    private EurekaClient client;

    private final List<DiscoveryClientResource> forkedDiscoveryClientResources = new ArrayList<>();
    private ApplicationInfoManager applicationInfoManager;

    DiscoveryClientResource(DiscoveryClientRuleBuilder builder) {
        this.registrationEnabled = builder.registrationEnabled;
        this.registryFetchEnabled = builder.registryFetchEnabled;
        this.portResolverCallable = builder.portResolverCallable;
        this.eurekaHttpServer = builder.eurekaHttpServer;
        this.instance = builder.instance;
        this.remoteRegions = builder.remoteRegions;
        this.vipFetch = builder.vipFetch;
        this.userName = builder.userName;
        this.password = builder.password;
    }

    public InstanceInfo getMyInstanceInfo() {
        return createApplicationManager().getInfo();
    }

    public EventBus getEventBus() {
        if (client == null) {
            getClient(); // Lazy initialization
        }
        return eventBus;
    }

    public ApplicationInfoManager getApplicationInfoManager() {
        return applicationInfoManager;
    }

    public EurekaClient getClient() {
        if (client == null) {
            try {
                applicationInfoManager = createApplicationManager();
                EurekaClientConfig clientConfig = createEurekaClientConfig();

                Jersey1DiscoveryClientOptionalArgs optionalArgs = new Jersey1DiscoveryClientOptionalArgs();
                eventBus = new EventBusImpl();
                optionalArgs.setEventBus(eventBus);

                client = new DiscoveryClient(applicationInfoManager, clientConfig, optionalArgs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return client;
    }

    public boolean awaitCacheUpdate(long timeout, TimeUnit unit) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        Object eventListener = new Object() {
            @Subscribe
            public void consume(CacheRefreshedEvent event) {
                latch.countDown();
            }
        };
        try {
            getEventBus().registerSubscriber(eventListener);
        } catch (InvalidSubscriberException e) {
            throw new IllegalStateException("Unexpected error during subscriber registration", e);
        }
        try {
            return latch.await(timeout, unit);
        } finally {
            getEventBus().unregisterSubscriber(eventListener);
        }
    }

    private ApplicationInfoManager createApplicationManager() {
        if (applicationManager == null) {
            EurekaInstanceConfig instanceConfig = new MyDataCenterInstanceConfig(EUREKA_TEST_NAMESPACE) {
                @Override
                public String getAppname() {
                    return "discoveryClientTest";
                }

                @Override
                public int getLeaseRenewalIntervalInSeconds() {
                    return 1;
                }
            };
            applicationManager = new ApplicationInfoManager(instanceConfig);
        }
        return applicationManager;
    }

    private EurekaClientConfig createEurekaClientConfig() throws Exception {
        // Cluster connectivity
        URI serviceURI;
        if (portResolverCallable != null) {
            serviceURI = new URI("http://localhost:" + portResolverCallable.call() + "/eureka/v2/");
        } else if (eurekaHttpServer != null) {
            serviceURI = eurekaHttpServer.getServiceURI();
        } else {
            throw new IllegalStateException("Either port or EurekaHttpServer must be configured");
        }

        if (userName != null) {
            serviceURI = UriBuilder.fromUri(serviceURI).userInfo(userName + ':' + password).build();
        }

        bindProperty(EUREKA_TEST_NAMESPACE + "serviceUrl.default", serviceURI.toString());
        if (remoteRegions != null && !remoteRegions.isEmpty()) {
            StringBuilder regions = new StringBuilder();
            for (String region : remoteRegions) {
                regions.append(',').append(region);
            }
            bindProperty(EUREKA_TEST_NAMESPACE + "fetchRemoteRegionsRegistry", regions.substring(1));
        }

        // Registration
        bindProperty(EUREKA_TEST_NAMESPACE + "registration.enabled", Boolean.toString(registrationEnabled));
        bindProperty(EUREKA_TEST_NAMESPACE + "appinfo.initial.replicate.time", Integer.toString(0));
        bindProperty(EUREKA_TEST_NAMESPACE + "appinfo.replicate.interval", Integer.toString(1));

        // Registry fetch
        bindProperty(EUREKA_TEST_NAMESPACE + "shouldFetchRegistry", Boolean.toString(registryFetchEnabled));

        bindProperty(EUREKA_TEST_NAMESPACE + "client.refresh.interval", Integer.toString(1));
        if (vipFetch != null) {
            bindProperty(EUREKA_TEST_NAMESPACE + "registryRefreshSingleVipAddress", vipFetch);
        }

        return new DefaultEurekaClientConfig(EUREKA_TEST_NAMESPACE);
    }

    @Override
    protected void after() {
        if (client != null) {
            client.shutdown();
        }
        for (DiscoveryClientResource resource : forkedDiscoveryClientResources) {
            resource.after();
        }
        for (String property : SYSTEM_PROPERTY_TRACKER) {
            ConfigurationManager.getConfigInstance().clearProperty(property);
        }
        clearDiscoveryClientConfig();
    }

    public DiscoveryClientRuleBuilder fork() {
        DiscoveryClientRuleBuilder builder = new DiscoveryClientRuleBuilder() {
            @Override
            public DiscoveryClientResource build() {
                DiscoveryClientResource clientResource = super.build();
                try {
                    clientResource.before();
                } catch (Throwable e) {
                    throw new IllegalStateException("Unexpected error during forking the client resource", e);
                }
                forkedDiscoveryClientResources.add(clientResource);
                return clientResource;
            }
        };
        return builder.withInstanceInfo(instance)
                .connectWith(eurekaHttpServer)
                .withPortResolver(portResolverCallable)
                .withRegistration(registrationEnabled)
                .withRegistryFetch(registryFetchEnabled)
                .withRemoteRegions(remoteRegions.toArray(new String[remoteRegions.size()]));
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

    private static void bindProperty(String propertyName, String value) {
        SYSTEM_PROPERTY_TRACKER.add(propertyName);
        ConfigurationManager.getConfigInstance().setProperty(propertyName, value);
    }

    public static class DiscoveryClientRuleBuilder {
        private boolean registrationEnabled;
        private boolean registryFetchEnabled;
        private Callable<Integer> portResolverCallable;
        private InstanceInfo instance;
        private SimpleEurekaHttpServer eurekaHttpServer;
        private List<String> remoteRegions;
        private String vipFetch;
        private String userName;
        private String password;

        public DiscoveryClientRuleBuilder withInstanceInfo(InstanceInfo instance) {
            this.instance = instance;
            return this;
        }

        public DiscoveryClientRuleBuilder withRegistration(boolean enabled) {
            this.registrationEnabled = enabled;
            return this;
        }

        public DiscoveryClientRuleBuilder withRegistryFetch(boolean enabled) {
            this.registryFetchEnabled = enabled;
            return this;
        }

        public DiscoveryClientRuleBuilder withPortResolver(Callable<Integer> portResolverCallable) {
            this.portResolverCallable = portResolverCallable;
            return this;
        }

        public DiscoveryClientRuleBuilder connectWith(SimpleEurekaHttpServer eurekaHttpServer) {
            this.eurekaHttpServer = eurekaHttpServer;
            return this;
        }

        public DiscoveryClientRuleBuilder withRemoteRegions(String... remoteRegions) {
            if (this.remoteRegions == null) {
                this.remoteRegions = new ArrayList<>();
            }
            Collections.addAll(this.remoteRegions, remoteRegions);
            return this;
        }

        public DiscoveryClientRuleBuilder withVipFetch(String vipFetch) {
            this.vipFetch = vipFetch;
            return this;
        }

        public DiscoveryClientRuleBuilder basicAuthentication(String userName, String password) {
            Preconditions.checkNotNull(userName, "HTTP basic authentication user name is null");
            Preconditions.checkNotNull(password, "HTTP basic authentication password is null");
            this.userName = userName;
            this.password = password;
            return this;
        }

        public DiscoveryClientResource build() {
            return new DiscoveryClientResource(this);
        }
    }
}

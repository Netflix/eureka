package com.netflix.discovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.inject.Inject;
import com.netflix.appinfo.EurekaAccept;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.Configuration;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;

@Configuration(prefix = "eureka")
@ConfigurationSource("eureka-client")
public class EurekaArchaius2ClientConfig implements EurekaClientConfig {
    public static final String DEFAULT_ZONE = "defaultZone";

    private static final String DEFAULT_NAMESPACE = "eureka";

    private final Config config;
    private final String defaultRegion;
    private final EurekaTransportConfig transportConfig;

    @Inject
    public EurekaArchaius2ClientConfig(Config config, EurekaTransportConfig transportConfig) {
        this(config, transportConfig, DEFAULT_NAMESPACE);
    }

    public EurekaArchaius2ClientConfig(Config config, EurekaTransportConfig transportConfig, String namespace) {
        this.defaultRegion = config.getString("@region", null);
        this.transportConfig = transportConfig;
        this.config = config.getPrefixedView(namespace);
    }

    public int getRegistryFetchIntervalSeconds() {
        return config.getInteger("client.refresh.interval", 30);
    }

    public int getInstanceInfoReplicationIntervalSeconds() {
        return config.getInteger("appinfo.replicate.interval", 30);
    }

    public int getInitialInstanceInfoReplicationIntervalSeconds() {
        return config.getInteger("appinfo.initial.replicate.time", 40);
    }

    public int getEurekaServiceUrlPollIntervalSeconds() {
        return config.getInteger("serviceUrlPollIntervalSeconds", 300);
    }

    public String getProxyHost() {
        return config.getString("eurekaServer.proxyHost", null);
    }

    public String getProxyPort() {
        return config.getString("eurekaServer.proxyPort", null);
    }

    public String getProxyUserName() {
        return config.getString("eurekaServer.proxyUserName", null);
    }

    public String getProxyPassword() {
        return config.getString("eurekaServer.proxyPassword", null);
    }

    public boolean shouldGZipContent() {
        return config.getBoolean("eurekaServer.gzipContent", true);
    }

    public int getEurekaServerReadTimeoutSeconds() {
        return config.getInteger("eurekaServer.readTimeout", 8);
    }

    public int getEurekaServerConnectTimeoutSeconds() {
        return config.getInteger("eurekaServer.connectTimeout", 5);
    }

    public String getBackupRegistryImpl() {
        return config.getString("eurekaServer.backupRegistry", null);
    }

    public int getEurekaServerTotalConnections() {
        return config.getInteger("eurekaServer.maxTotalConnections", 200);
    }

    public int getEurekaServerTotalConnectionsPerHost() {
        return config.getInteger("eurekaServer.maxConnectionsPerHost", 50);
    }

    public String getEurekaServerURLContext() {
        return config.getString("eurekaServer.context", null);
    }

    public String getEurekaServerPort() {
        return config.getString("eurekaServer.port", null);
    }

    public String getEurekaServerDNSName() {
        return config.getString("eurekaServer.domainName", null);
    }

    public boolean shouldUseDnsForFetchingServiceUrls() {
        return config.getBoolean("shouldUseDns", false);
    }

    public boolean shouldRegisterWithEureka() {
        return config.getBoolean("registration.enabled", true);
    }

    public boolean shouldPreferSameZoneEureka() {
        return config.getBoolean("preferSameZone", true);
    }

    public boolean allowRedirects() {
        return config.getBoolean("allowRedirects", false);
    }

    public boolean shouldLogDeltaDiff() {
        return config.getBoolean("printDeltaFullDiff", false);
    }

    public boolean shouldDisableDelta() {
        return config.getBoolean("disableDelta", false);
    }

    public String fetchRegistryForRemoteRegions() {
        return config.getString("fetchRemoteRegionsRegistry", null);
    }

    public String getRegion() {
        return config.getString("region", defaultRegion);
    }

    public String[] getAvailabilityZones(String region) {
        return config.getString(String.format("%s.availabilityZones", region), DEFAULT_ZONE).split(",");
    }

    public List<String> getEurekaServerServiceUrls(String myZone) {
        String serviceUrls = config.getString("serviceUrl." + myZone, null);
        if (serviceUrls == null || serviceUrls.isEmpty()) {
            serviceUrls = config.getString("serviceUrl." + "default", null);
        }

        return serviceUrls != null
                ? Arrays.asList(serviceUrls.split(","))
                : Collections.<String>emptyList();
    }

    public boolean shouldFilterOnlyUpInstances() {
        return config.getBoolean("shouldFilterOnlyUpInstances", true);
    }

    public int getEurekaConnectionIdleTimeoutSeconds() {
        return config.getInteger("eurekaserver.connectionIdleTimeoutInSeconds", 30);
    }

    public boolean shouldFetchRegistry() {
        return config.getBoolean("shouldFetchRegistry", true);
    }

    public String getRegistryRefreshSingleVipAddress() {
        return config.getString("registryRefreshSingleVipAddress", null);
    }

    public int getHeartbeatExecutorThreadPoolSize() {
        return config.getInteger("client.heartbeat.threadPoolSize", 2);
    }

    public int getHeartbeatExecutorExponentialBackOffBound() {
        return config.getInteger("client.heartbeat.exponentialBackOffBound", 10);
    }

    public int getCacheRefreshExecutorThreadPoolSize() {
        return config.getInteger("client.cacheRefresh.threadPoolSize", 10);
    }

    public int getCacheRefreshExecutorExponentialBackOffBound() {
        return config.getInteger("client.cacheRefresh.exponentialBackOffBound", 10);
    }

    public String getDollarReplacement() {
        return config.getString("dollarReplacement", "_-");
    }

    public String getEscapeCharReplacement() {
        return config.getString("escapeCharReplacement", "__");
    }

    public boolean shouldOnDemandUpdateStatusChange() {
        return config.getBoolean("shouldOnDemandUpdateStatusChange", true);
    }

    @Override
    public String getEncoderName() {
        return config.getString("encoderName", null);
    }

    @Override
    public String getDecoderName() {
        return config.getString("decoderName", null);
    }

    @Override
    public String getClientDataAccept() {
        return config.getString("clientDataAccept", EurekaAccept.full.name());
    }

    @Override
    public String getExperimental(String name) {
        return config.getString("experimental." + name, null);
    }

    @Override
    public EurekaTransportConfig getTransportConfig() {
        return transportConfig;
    }
}

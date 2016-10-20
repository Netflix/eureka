package com.netflix.discovery;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.inject.Inject;
import com.netflix.appinfo.EurekaAccept;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.internal.util.InternalPrefixedConfig;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;

import javax.inject.Singleton;

import static com.netflix.discovery.PropertyBasedClientConfigConstants.*;

@Singleton
@ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
public class EurekaArchaius2ClientConfig implements EurekaClientConfig {
    public static final String DEFAULT_ZONE = "defaultZone";

    private static final String DEFAULT_NAMESPACE = "eureka";

    private final Config configInstance;
    private final InternalPrefixedConfig prefixedConfig;
    private final EurekaTransportConfig transportConfig;

    @Inject
    public EurekaArchaius2ClientConfig(Config configInstance, EurekaTransportConfig transportConfig) {
        this(configInstance, transportConfig, DEFAULT_NAMESPACE);
    }

    public EurekaArchaius2ClientConfig(Config configInstance, EurekaTransportConfig transportConfig, String namespace) {
        this.transportConfig = transportConfig;
        this.configInstance = configInstance;
        this.prefixedConfig = new InternalPrefixedConfig(configInstance, namespace);
    }

    public int getRegistryFetchIntervalSeconds() {
        return prefixedConfig.getInteger(REGISTRY_REFRESH_INTERVAL_KEY, 30);
    }

    public int getInstanceInfoReplicationIntervalSeconds() {
        return prefixedConfig.getInteger(REGISTRATION_REPLICATION_INTERVAL_KEY, 30);
    }

    public int getInitialInstanceInfoReplicationIntervalSeconds() {
        return prefixedConfig.getInteger(INITIAL_REGISTRATION_REPLICATION_DELAY_KEY, 40);
    }

    public int getEurekaServiceUrlPollIntervalSeconds() {
        return prefixedConfig.getInteger(EUREKA_SERVER_URL_POLL_INTERVAL_KEY, 300);
    }

    public String getProxyHost() {
        return prefixedConfig.getString(EUREKA_SERVER_PROXY_HOST_KEY, null);
    }

    public String getProxyPort() {
        return prefixedConfig.getString(EUREKA_SERVER_PROXY_PORT_KEY, null);
    }

    public String getProxyUserName() {
        return prefixedConfig.getString(EUREKA_SERVER_PROXY_USERNAME_KEY, null);
    }

    public String getProxyPassword() {
        return prefixedConfig.getString(EUREKA_SERVER_PROXY_PASSWORD_KEY, null);
    }

    public boolean shouldGZipContent() {
        return prefixedConfig.getBoolean(EUREKA_SERVER_GZIP_CONTENT_KEY, true);
    }

    public int getEurekaServerReadTimeoutSeconds() {
        return prefixedConfig.getInteger(EUREKA_SERVER_READ_TIMEOUT_KEY, 8);
    }

    public int getEurekaServerConnectTimeoutSeconds() {
        return prefixedConfig.getInteger(EUREKA_SERVER_CONNECT_TIMEOUT_KEY, 5);
    }

    public String getBackupRegistryImpl() {
        return prefixedConfig.getString(BACKUP_REGISTRY_CLASSNAME_KEY, null);
    }

    public int getEurekaServerTotalConnections() {
        return prefixedConfig.getInteger(EUREKA_SERVER_MAX_CONNECTIONS_KEY, 200);
    }

    public int getEurekaServerTotalConnectionsPerHost() {
        return prefixedConfig.getInteger(EUREKA_SERVER_MAX_CONNECTIONS_PER_HOST_KEY, 50);
    }

    public String getEurekaServerURLContext() {
        return prefixedConfig.getString(EUREKA_SERVER_URL_CONTEXT_KEY, null);
    }

    public String getEurekaServerPort() {
        return prefixedConfig.getString(
                EUREKA_SERVER_PORT_KEY,
                prefixedConfig.getString(EUREKA_SERVER_FALLBACK_PORT_KEY, null)
        );
    }

    public String getEurekaServerDNSName() {
        return prefixedConfig.getString(
                EUREKA_SERVER_DNS_NAME_KEY,
                prefixedConfig.getString(EUREKA_SERVER_FALLBACK_DNS_NAME_KEY, null)
        );
    }

    public boolean shouldUseDnsForFetchingServiceUrls() {
        return prefixedConfig.getBoolean(SHOULD_USE_DNS_KEY, false);
    }

    public boolean shouldRegisterWithEureka() {
        return prefixedConfig.getBoolean(REGISTRATION_ENABLED_KEY, true);
    }

    public boolean shouldPreferSameZoneEureka() {
        return prefixedConfig.getBoolean(SHOULD_PREFER_SAME_ZONE_SERVER_KEY, true);
    }

    public boolean allowRedirects() {
        return prefixedConfig.getBoolean(SHOULD_ALLOW_REDIRECTS_KEY, false);
    }

    public boolean shouldLogDeltaDiff() {
        return prefixedConfig.getBoolean(SHOULD_LOG_DELTA_DIFF_KEY, false);
    }

    public boolean shouldDisableDelta() {
        return prefixedConfig.getBoolean(SHOULD_DISABLE_DELTA_KEY, false);
    }

    public String fetchRegistryForRemoteRegions() {
        return prefixedConfig.getString(SHOULD_FETCH_REMOTE_REGION_KEY, null);
    }

    public String getRegion() {
        return prefixedConfig.getString(
                CLIENT_REGION_KEY,
                prefixedConfig.getString(CLIENT_REGION_FALLBACK_KEY, Values.DEFAULT_CLIENT_REGION)
        );
    }

    public String[] getAvailabilityZones(String region) {
        return prefixedConfig.getString(String.format("%s." + CONFIG_AVAILABILITY_ZONE_PREFIX, region), DEFAULT_ZONE).split(",");
    }

    public List<String> getEurekaServerServiceUrls(String myZone) {
        String serviceUrls = prefixedConfig.getString(CONFIG_EUREKA_SERVER_SERVICE_URL_PREFIX + "." + myZone, null);
        if (serviceUrls == null || serviceUrls.isEmpty()) {
            serviceUrls = prefixedConfig.getString(CONFIG_EUREKA_SERVER_SERVICE_URL_PREFIX + ".default", null);
        }

        return serviceUrls != null
                ? Arrays.asList(serviceUrls.split(","))
                : Collections.<String>emptyList();
    }

    public boolean shouldFilterOnlyUpInstances() {
        return prefixedConfig.getBoolean(SHOULD_FILTER_ONLY_UP_INSTANCES_KEY, true);
    }

    public int getEurekaConnectionIdleTimeoutSeconds() {
        return prefixedConfig.getInteger(EUREKA_SERVER_CONNECTION_IDLE_TIMEOUT_KEY, 30);
    }

    public boolean shouldFetchRegistry() {
        return prefixedConfig.getBoolean(FETCH_REGISTRY_ENABLED_KEY, true);
    }

    public String getRegistryRefreshSingleVipAddress() {
        return prefixedConfig.getString(FETCH_SINGLE_VIP_ONLY_KEY, null);
    }

    public int getHeartbeatExecutorThreadPoolSize() {
        return prefixedConfig.getInteger(HEARTBEAT_THREADPOOL_SIZE_KEY, Values.DEFAULT_EXECUTOR_THREAD_POOL_SIZE);
    }

    public int getHeartbeatExecutorExponentialBackOffBound() {
        return prefixedConfig.getInteger(HEARTBEAT_BACKOFF_BOUND_KEY, Values.DEFAULT_EXECUTOR_THREAD_POOL_BACKOFF_BOUND);
    }

    public int getCacheRefreshExecutorThreadPoolSize() {
        return prefixedConfig.getInteger(CACHEREFRESH_THREADPOOL_SIZE_KEY, Values.DEFAULT_EXECUTOR_THREAD_POOL_SIZE);
    }

    public int getCacheRefreshExecutorExponentialBackOffBound() {
        return prefixedConfig.getInteger(CACHEREFRESH_BACKOFF_BOUND_KEY, Values.DEFAULT_EXECUTOR_THREAD_POOL_BACKOFF_BOUND);
    }

    public String getDollarReplacement() {
        return prefixedConfig.getString(CONFIG_DOLLAR_REPLACEMENT_KEY, Values.CONFIG_DOLLAR_REPLACEMENT);
    }

    public String getEscapeCharReplacement() {
        return prefixedConfig.getString(CONFIG_ESCAPE_CHAR_REPLACEMENT_KEY, Values.CONFIG_ESCAPE_CHAR_REPLACEMENT);
    }

    public boolean shouldOnDemandUpdateStatusChange() {
        return prefixedConfig.getBoolean(SHOULD_ONDEMAND_UPDATE_STATUS_KEY, true);
    }

    @Override
    public String getEncoderName() {
        return prefixedConfig.getString(CLIENT_ENCODER_NAME_KEY, null);
    }

    @Override
    public String getDecoderName() {
        return prefixedConfig.getString(CLIENT_DECODER_NAME_KEY, null);
    }

    @Override
    public String getClientDataAccept() {
        return prefixedConfig.getString(CLIENT_DATA_ACCEPT_KEY, EurekaAccept.full.name());
    }

    @Override
    public String getExperimental(String name) {
        return prefixedConfig.getString(CONFIG_EXPERIMENTAL_PREFIX + "." + name, null);
    }

    @Override
    public EurekaTransportConfig getTransportConfig() {
        return transportConfig;
    }
}

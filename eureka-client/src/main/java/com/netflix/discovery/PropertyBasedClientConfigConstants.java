package com.netflix.discovery;

/**
 * constants pertaining to property based client configs
 *
 * @author David Liu
 */
final class PropertyBasedClientConfigConstants {
    static final String CLIENT_REGION_FALLBACK_KEY = "eureka.region";

    // NOTE: all keys are before any prefixes are applied
    static final String CLIENT_REGION_KEY = "region";

    static final String REGISTRATION_ENABLED_KEY = "registration.enabled";
    static final String FETCH_REGISTRY_ENABLED_KEY = "shouldFetchRegistry";

    static final String REGISTRY_REFRESH_INTERVAL_KEY = "client.refresh.interval";
    static final String REGISTRATION_REPLICATION_INTERVAL_KEY = "appinfo.replicate.interval";
    static final String INITIAL_REGISTRATION_REPLICATION_DELAY_KEY = "appinfo.initial.replicate.time";
    static final String HEARTBEAT_THREADPOOL_SIZE_KEY = "client.heartbeat.threadPoolSize";
    static final String HEARTBEAT_BACKOFF_BOUND_KEY = "client.heartbeat.exponentialBackOffBound";
    static final String CACHEREFRESH_THREADPOOL_SIZE_KEY = "client.cacheRefresh.threadPoolSize";
    static final String CACHEREFRESH_BACKOFF_BOUND_KEY = "client.cacheRefresh.exponentialBackOffBound";

    static final String SHOULD_ONDEMAND_UPDATE_STATUS_KEY = "shouldOnDemandUpdateStatusChange";
    static final String SHOULD_DISABLE_DELTA_KEY = "disableDelta";
    static final String SHOULD_FETCH_REMOTE_REGION_KEY = "fetchRemoteRegionsRegistry";
    static final String SHOULD_FILTER_ONLY_UP_INSTANCES_KEY = "shouldFilterOnlyUpInstances";
    static final String FETCH_SINGLE_VIP_ONLY_KEY = "registryRefreshSingleVipAddress";
    static final String CLIENT_ENCODER_NAME_KEY = "encoderName";
    static final String CLIENT_DECODER_NAME_KEY = "decoderName";
    static final String CLIENT_DATA_ACCEPT_KEY = "clientDataAccept";

    static final String BACKUP_REGISTRY_CLASSNAME_KEY = "backupregistry";

    static final String SHOULD_PREFER_SAME_ZONE_SERVER_KEY = "preferSameZone";
    static final String SHOULD_ALLOW_REDIRECTS_KEY = "allowRedirects";
    static final String SHOULD_USE_DNS_KEY = "shouldUseDns";

    static final String EUREKA_SERVER_URL_POLL_INTERVAL_KEY = "serviceUrlPollIntervalMs";
    static final String EUREKA_SERVER_URL_CONTEXT_KEY = "eurekaServer.context";
    static final String EUREKA_SERVER_FALLBACK_URL_CONTEXT_KEY = "context";
    static final String EUREKA_SERVER_PORT_KEY = "eurekaServer.port";
    static final String EUREKA_SERVER_FALLBACK_PORT_KEY = "port";
    static final String EUREKA_SERVER_DNS_NAME_KEY = "eurekaServer.domainName";
    static final String EUREKA_SERVER_FALLBACK_DNS_NAME_KEY = "domainName";

    static final String EUREKA_SERVER_PROXY_HOST_KEY = "eurekaServer.proxyHost";
    static final String EUREKA_SERVER_PROXY_PORT_KEY = "eurekaServer.proxyPort";
    static final String EUREKA_SERVER_PROXY_USERNAME_KEY = "eurekaServer.proxyUserName";
    static final String EUREKA_SERVER_PROXY_PASSWORD_KEY = "eurekaServer.proxyPassword";

    static final String EUREKA_SERVER_GZIP_CONTENT_KEY = "eurekaServer.gzipContent";
    static final String EUREKA_SERVER_READ_TIMEOUT_KEY = "eurekaServer.readTimeout";
    static final String EUREKA_SERVER_CONNECT_TIMEOUT_KEY = "eurekaServer.connectTimeout";
    static final String EUREKA_SERVER_MAX_CONNECTIONS_KEY = "eurekaServer.maxTotalConnections";
    static final String EUREKA_SERVER_MAX_CONNECTIONS_PER_HOST_KEY = "eurekaServer.maxConnectionsPerHost";
    // yeah the case on eurekaserver is different, backwards compatibility requirements :(
    static final String EUREKA_SERVER_CONNECTION_IDLE_TIMEOUT_KEY = "eurekaserver.connectionIdleTimeoutInSeconds";

    static final String SHOULD_LOG_DELTA_DIFF_KEY = "printDeltaFullDiff";

    static final String CONFIG_DOLLAR_REPLACEMENT_KEY = "dollarReplacement";
    static final String CONFIG_ESCAPE_CHAR_REPLACEMENT_KEY = "escapeCharReplacement";


    // additional namespaces
    static final String CONFIG_EXPERIMENTAL_PREFIX = "experimental";
    static final String CONFIG_AVAILABILITY_ZONE_PREFIX = "availabilityZones";
    static final String CONFIG_EUREKA_SERVER_SERVICE_URL_PREFIX = "serviceUrl";


    static class Values {
        static final String CONFIG_DOLLAR_REPLACEMENT = "_-";
        static final String CONFIG_ESCAPE_CHAR_REPLACEMENT = "__";

        static final String DEFAULT_CLIENT_REGION = "us-east-1";

        static final int DEFAULT_EXECUTOR_THREAD_POOL_SIZE = 5;
        static final int DEFAULT_EXECUTOR_THREAD_POOL_BACKOFF_BOUND = 10;
    }
}

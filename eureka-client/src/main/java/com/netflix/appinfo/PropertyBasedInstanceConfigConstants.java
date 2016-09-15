package com.netflix.appinfo;

/**
 * constants pertaining to property based instance configs
 *
 * @author David Liu
 */
final class PropertyBasedInstanceConfigConstants {

    // NOTE: all keys are before any prefixes are applied
    static final String INSTANCE_ID_KEY = "instanceId";
    static final String APP_NAME_KEY = "name";
    static final String APP_GROUP_KEY = "appGroup";
    static final String FALLBACK_APP_GROUP_KEY = "NETFLIX_APP_GROUP";
    static final String ASG_NAME_KEY = "asgName";

    static final String PORT_KEY = "port";
    static final String SECURE_PORT_KEY = "securePort";
    static final String PORT_ENABLED_KEY = PORT_KEY + ".enabled";
    static final String SECURE_PORT_ENABLED_KEY = SECURE_PORT_KEY + ".enabled";

    static final String VIRTUAL_HOSTNAME_KEY = "vipAddress";
    static final String SECURE_VIRTUAL_HOSTNAME_KEY = "secureVipAddress";

    static final String STATUS_PAGE_URL_PATH_KEY = "statusPageUrlPath";
    static final String STATUS_PAGE_URL_KEY = "statusPageUrl";
    static final String HOME_PAGE_URL_PATH_KEY = "homePageUrlPath";
    static final String HOME_PAGE_URL_KEY = "homePageUrl";
    static final String HEALTHCHECK_URL_PATH_KEY = "healthCheckUrlPath";
    static final String HEALTHCHECK_URL_KEY = "healthCheckUrl";
    static final String SECURE_HEALTHCHECK_URL_KEY = "secureHealthCheckUrl";

    static final String LEASE_RENEWAL_INTERVAL_KEY = "lease.renewalInterval";
    static final String LEASE_EXPIRATION_DURATION_KEY = "lease.duration";

    static final String INSTANCE_METADATA_PREFIX = "metadata";

    static final String DEFAULT_ADDRESS_RESOLUTION_ORDER_KEY = "defaultAddressResolutionOrder";
    static final String TRAFFIC_ENABLED_ON_INIT_KEY = "traffic.enabled";


    static class Values {
        static final String UNKNOWN_APPLICATION = "unknown";

        static final String DEFAULT_STATUSPAGE_URLPATH = "/Status";
        static final String DEFAULT_HOMEPAGE_URLPATH = "/";
        static final String DEFAULT_HEALTHCHECK_URLPATH = "/healthcheck";
    }
}

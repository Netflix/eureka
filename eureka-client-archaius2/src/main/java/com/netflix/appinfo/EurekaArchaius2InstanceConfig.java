package com.netflix.appinfo;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.Sets;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.CommonConstants;
import com.netflix.discovery.internal.util.InternalPrefixedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.appinfo.PropertyBasedInstanceConfigConstants.*;

@Singleton
@ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
public class EurekaArchaius2InstanceConfig extends AbstractInstanceConfig {
    private static final Logger logger = LoggerFactory.getLogger(EurekaArchaius2InstanceConfig.class);

    protected String namespace;

    private final Config configInstance;
    private final InternalPrefixedConfig prefixedConfig;
    private final DataCenterInfo dcInfo;

    private final String defaultAppGroup;
    
    @Inject
    public EurekaArchaius2InstanceConfig(Config configInstance) {
        this(configInstance, CommonConstants.DEFAULT_CONFIG_NAMESPACE);
    }
    
    public EurekaArchaius2InstanceConfig(Config configInstance, String namespace) {
        this(configInstance, namespace, new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
    }
    
    public EurekaArchaius2InstanceConfig(Config configInstance, String namespace, DataCenterInfo dcInfo) {
        this.defaultAppGroup = configInstance.getString(FALLBACK_APP_GROUP_KEY, Values.UNKNOWN_APPLICATION);
        this.namespace = namespace;
        this.configInstance = configInstance;
        this.prefixedConfig = new InternalPrefixedConfig(configInstance, namespace);
        this.dcInfo = dcInfo;
    }

    @Override
    public String getInstanceId() {
        String result = prefixedConfig.getString(INSTANCE_ID_KEY, null);
        return result == null ? null : result.trim();
    }

    @Override
    public String getAppname() {
        return prefixedConfig.getString(APP_NAME_KEY, Values.UNKNOWN_APPLICATION).trim();
    }

    @Override
    public String getAppGroupName() {
        return prefixedConfig.getString(APP_GROUP_KEY, defaultAppGroup).trim();
    }

    @Override
    public boolean isInstanceEnabledOnit() {
        return prefixedConfig.getBoolean(TRAFFIC_ENABLED_ON_INIT_KEY, super.isInstanceEnabledOnit());
    }

    @Override
    public int getNonSecurePort() {
        return prefixedConfig.getInteger(PORT_KEY, super.getNonSecurePort());
    }

    @Override
    public int getSecurePort() {
        return prefixedConfig.getInteger(SECURE_PORT_KEY, super.getSecurePort());
    }

    @Override
    public boolean isNonSecurePortEnabled() {
        return prefixedConfig.getBoolean(PORT_ENABLED_KEY, super.isNonSecurePortEnabled());
    }

    @Override
    public boolean getSecurePortEnabled() {
        return prefixedConfig.getBoolean(SECURE_PORT_ENABLED_KEY, super.getSecurePortEnabled());
    }

    @Override
    public int getLeaseRenewalIntervalInSeconds() {
        return prefixedConfig.getInteger(LEASE_RENEWAL_INTERVAL_KEY, super.getLeaseRenewalIntervalInSeconds());
    }

    @Override
    public int getLeaseExpirationDurationInSeconds() {
        return prefixedConfig.getInteger(LEASE_EXPIRATION_DURATION_KEY, super.getLeaseExpirationDurationInSeconds());
    }

    @Override
    public String getVirtualHostName() {
        return this.isNonSecurePortEnabled()
            ? prefixedConfig.getString(VIRTUAL_HOSTNAME_KEY, super.getVirtualHostName())
            : null;
    }

    @Override
    public String getSecureVirtualHostName() {
        return this.getSecurePortEnabled()
            ? prefixedConfig.getString(SECURE_VIRTUAL_HOSTNAME_KEY, super.getSecureVirtualHostName())
            : null;
    }

    @Override
    public String getASGName() {
        return prefixedConfig.getString(ASG_NAME_KEY, super.getASGName());
    }

    @Override
    public Map<String, String> getMetadataMap() {
        Map<String, String> meta = new HashMap<>();
        InternalPrefixedConfig metadataConfig = new InternalPrefixedConfig(configInstance, namespace, INSTANCE_METADATA_PREFIX);
        for (String key : Sets.newHashSet(metadataConfig.getKeys())) {
            String value = metadataConfig.getString(key, null);
            // only add the metadata if the value is present
            if (value != null && !value.isEmpty()) {
                meta.put(key, value);
            } else {
                logger.warn("Not adding metadata with key \"{}\" as it has null or empty value", key);
            }
        }
        return meta;
    }

    @Override
    public DataCenterInfo getDataCenterInfo() {
        return dcInfo;
    }

    @Override
    public String getStatusPageUrlPath() {
        return prefixedConfig.getString(STATUS_PAGE_URL_PATH_KEY, Values.DEFAULT_STATUSPAGE_URLPATH);
    }

    @Override
    public String getStatusPageUrl() {
        return prefixedConfig.getString(STATUS_PAGE_URL_KEY, null);
    }

    @Override
    public String getHomePageUrlPath() {
        return prefixedConfig.getString(HOME_PAGE_URL_PATH_KEY, Values.DEFAULT_HOMEPAGE_URLPATH);
    }

    @Override
    public String getHomePageUrl() {
        return prefixedConfig.getString(HOME_PAGE_URL_KEY, null);
    }

    @Override
    public String getHealthCheckUrlPath() {
        return prefixedConfig.getString(HEALTHCHECK_URL_PATH_KEY, Values.DEFAULT_HEALTHCHECK_URLPATH);
    }

    @Override
    public String getHealthCheckUrl() {
        return prefixedConfig.getString(HEALTHCHECK_URL_KEY, null);
    }

    @Override
    public String getSecureHealthCheckUrl() {
        return prefixedConfig.getString(SECURE_HEALTHCHECK_URL_KEY, null);
    }

    @Override
    public String[] getDefaultAddressResolutionOrder() {
        String result = prefixedConfig.getString(DEFAULT_ADDRESS_RESOLUTION_ORDER_KEY, null);
        return result == null ? new String[0] : result.split(",");
    }

    @Override
    public String getNamespace() {
        return namespace;
    }
}

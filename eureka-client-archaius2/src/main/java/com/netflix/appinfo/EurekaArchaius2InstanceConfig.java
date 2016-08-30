package com.netflix.appinfo;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.Sets;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;

import static com.netflix.appinfo.PropertyBasedInstanceConfigConstants.*;

@Singleton
@ConfigurationSource(Values.DEFAULT_CONFIG_FILE_NAME)
public class EurekaArchaius2InstanceConfig extends AbstractInstanceConfig {

    protected String namespace;

    private Config config;
    private final DataCenterInfo dcInfo;

    private final String defaultAppGroup;
    
    @Inject
    public EurekaArchaius2InstanceConfig(Config config) {
        this(config, Values.DEFAULT_NAMESPACE);
    }
    
    public EurekaArchaius2InstanceConfig(Config config, String namespace) {
        this(config, namespace, new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        });
    }
    
    public EurekaArchaius2InstanceConfig(Config config, String namespace, DataCenterInfo dcInfo) {
        this.defaultAppGroup = config.getString(FALLBACK_APP_GROUP_KEY, Values.UNKNOWN_APPLICATION);
        
        this.namespace = namespace;
        this.config = config.getPrefixedView(namespace);
        this.dcInfo = dcInfo;
    }

    @Override
    public String getInstanceId() {
        String result = config.getString(INSTANCE_ID_KEY, null);
        return result == null ? null : result.trim();
    }

    @Override
    public String getAppname() {
        return config.getString(APP_NAME_KEY, Values.UNKNOWN_APPLICATION).trim();
    }

    @Override
    public String getAppGroupName() {
        return config.getString(APP_GROUP_KEY, defaultAppGroup).trim();
    }

    @Override
    public boolean isInstanceEnabledOnit() {
        return config.getBoolean(TRAFFIC_ENABLED_ON_INIT_KEY, super.isInstanceEnabledOnit());
    }

    @Override
    public int getNonSecurePort() {
        return config.getInteger(PORT_KEY, super.getNonSecurePort());
    }

    @Override
    public int getSecurePort() {
        return config.getInteger(SECURE_PORT_KEY, super.getSecurePort());
    }

    @Override
    public boolean isNonSecurePortEnabled() {
        return config.getBoolean(PORT_ENABLED_KEY, super.isNonSecurePortEnabled());
    }

    @Override
    public boolean getSecurePortEnabled() {
        return config.getBoolean(SECURE_PORT_ENABLED_KEY, super.getSecurePortEnabled());
    }

    @Override
    public int getLeaseRenewalIntervalInSeconds() {
        return config.getInteger(LEASE_RENEWAL_INTERVAL_KEY, super.getLeaseRenewalIntervalInSeconds());
    }

    @Override
    public int getLeaseExpirationDurationInSeconds() {
        return config.getInteger(LEASE_EXPIRATION_DURATION_KEY, super.getLeaseExpirationDurationInSeconds());
    }

    @Override
    public String getVirtualHostName() {
        return this.isNonSecurePortEnabled()
            ? config.getString(VIRTUAL_HOSTNAME_KEY, super.getVirtualHostName())
            : null;
    }

    @Override
    public String getSecureVirtualHostName() {
        return this.getSecurePortEnabled()
            ? config.getString(SECURE_VIRTUAL_HOSTNAME_KEY, super.getSecureVirtualHostName())
            : null;
    }

    @Override
    public String getASGName() {
        return config.getString(ASG_NAME_KEY, super.getASGName());
    }

    @Override
    public Map<String, String> getMetadataMap() {
        Map<String, String> meta = new HashMap<>(); 
        Config config = this.config.getPrefixedView(INSTANCE_METADATA_PREFIX);
        for (String key : Sets.newHashSet(config.getKeys())) {
            meta.put(key, config.getString(key));
        }
        return meta;
    }

    @Override
    public DataCenterInfo getDataCenterInfo() {
        return dcInfo;
    }

    @Override
    public String getStatusPageUrlPath() {
        return config.getString(STATUS_PAGE_URL_PATH_KEY, Values.DEFAULT_STATUSPAGE_URLPATH);
    }

    @Override
    public String getStatusPageUrl() {
        return config.getString(STATUS_PAGE_URL_KEY, null);
    }

    @Override
    public String getHomePageUrlPath() {
        return config.getString(HOME_PAGE_URL_PATH_KEY, Values.DEFAULT_HOMEPAGE_URLPATH);
    }

    @Override
    public String getHomePageUrl() {
        return config.getString(HOME_PAGE_URL_KEY, null);
    }

    @Override
    public String getHealthCheckUrlPath() {
        return config.getString(HEALTHCHECK_URL_PATH_KEY, Values.DEFAULT_HEALTHCHECK_URLPATH);
    }

    @Override
    public String getHealthCheckUrl() {
        return config.getString(HEALTHCHECK_URL_KEY, null);
    }

    @Override
    public String getSecureHealthCheckUrl() {
        return config.getString(SECURE_HEALTHCHECK_URL_KEY, null);
    }

    @Override
    public String[] getDefaultAddressResolutionOrder() {
        String result = config.getString(DEFAULT_ADDRESS_RESOLUTION_ORDER_KEY, null);
        return result == null ? new String[0] : result.split(",");
    }

    @Override
    public String getNamespace() {
        return namespace;
    }
}

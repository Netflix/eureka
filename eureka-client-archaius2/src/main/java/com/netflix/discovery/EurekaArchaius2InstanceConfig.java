package com.netflix.discovery;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.Sets;
import com.netflix.appinfo.AbstractInstanceConfig;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;

@Singleton
@ConfigurationSource("eureka-client")
public class EurekaArchaius2InstanceConfig extends AbstractInstanceConfig {
    private static final String UNKNOWN_APPLICATION = "unknown";

    private Config config;
    private String namespace;
    private final DataCenterInfo dcInfo;

    private final String defaultAppGroup;
    
    @Inject
    public EurekaArchaius2InstanceConfig(Config config) {
        this(config, DEFAULT_NAMESPACE);
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
        this.defaultAppGroup = config.getString("NETFLIX_APP_GROUP", null);
        
        this.namespace = namespace;
        this.config = config.getPrefixedView(namespace);
        this.dcInfo = dcInfo;
        
        // TODO: archaius.deployment.environment=${env}?
    }

    @Override
    public String getInstanceId() {
        return config.getString("instanceId", null);
    }

    @Override
    public String getAppname() {
        return config.getString("name", UNKNOWN_APPLICATION);
    }

    @Override
    public String getAppGroupName() {
        return config.getString("appGroup", defaultAppGroup);
    }

    @Override
    public boolean isInstanceEnabledOnit() {
        return config.getBoolean("traffic.enabled", super.isInstanceEnabledOnit());
    }

    @Override
    public int getNonSecurePort() {
        return config.getInteger("port", super.getNonSecurePort());
    }

    @Override
    public int getSecurePort() {
        return config.getInteger("securePort", super.getSecurePort());
    }

    @Override
    public boolean isNonSecurePortEnabled() {
        return config.getBoolean("port.enabled", super.isNonSecurePortEnabled());
    }

    @Override
    public boolean getSecurePortEnabled() {
        return config.getBoolean("securePort.enabled", super.getSecurePortEnabled());
    }

    @Override
    public int getLeaseRenewalIntervalInSeconds() {
        return config.getInteger("lease.renewalInterval", super.getLeaseRenewalIntervalInSeconds());
    }

    @Override
    public int getLeaseExpirationDurationInSeconds() {
        return config.getInteger("lease.duration", super.getLeaseExpirationDurationInSeconds());
    }

    @Override
    public String getVirtualHostName() {
        return this.isNonSecurePortEnabled()
            ? config.getString("vipAddress", super.getVirtualHostName())
            : null;
    }

    @Override
    public String getSecureVirtualHostName() {
        return this.getSecurePortEnabled()
            ? config.getString("secureVipAddress", null)
            : null;
    }

    @Override
    public String getASGName() {
        return config.getString("asgName", super.getASGName());
    }

    @Override
    public Map<String, String> getMetadataMap() {
        Map<String, String> meta = new HashMap<>(); 
        Config config = this.config.getPrefixedView("metadata");
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
        return config.getString("statusPageUrlPath", "/Status");
    }

    @Override
    public String getStatusPageUrl() {
        return config.getString("statusPageUrl", null);
    }

    @Override
    public String getHomePageUrlPath() {
        return config.getString("homePageUrlPath", "/");
    }

    @Override
    public String getHomePageUrl() {
        return config.getString("homePageUrl", null);
    }

    @Override
    public String getHealthCheckUrlPath() {
        return config.getString("healthCheckUrlPath", "/healthcheck");
    }

    @Override
    public String getHealthCheckUrl() {
        return config.getString("healthCheckUr", null);
    }

    @Override
    public String getSecureHealthCheckUrl() {
        return config.getString("secureHealthCheckUrl", null);
    }

    @Override
    public String[] getDefaultAddressResolutionOrder() {
        String result = config.getString(namespace + "defaultAddressResolutionOrder", null);
        return result == null ? new String[0] : result.split(",");
    }

    @Override
    public String getNamespace() {
        return namespace;
    }
}

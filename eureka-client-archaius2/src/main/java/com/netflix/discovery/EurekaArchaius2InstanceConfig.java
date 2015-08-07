package com.netflix.discovery;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.common.collect.Sets;
import com.netflix.appinfo.AbstractInstanceConfig;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.archaius.Config;
import com.netflix.archaius.annotations.ConfigurationSource;

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
        this(config, "eureka");
    }
    
    public EurekaArchaius2InstanceConfig(Config config, String namespace) {
        this(config, "eureka", new DataCenterInfo() {
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
    
    public String getAppname() {
        return config.getString("name", UNKNOWN_APPLICATION);
    }

    public String getAppGroupName() {
        return config.getString("appGroup", defaultAppGroup);
    }

    public boolean isInstanceEnabledOnit() {
        return config.getBoolean("traffic.enabled", super.isInstanceEnabledOnit());
    }

    public int getNonSecurePort() {
        return config.getInteger("port", super.getNonSecurePort());
    }

    public int getSecurePort() {
        return config.getInteger("securePort", super.getSecurePort());
    }

    public boolean isNonSecurePortEnabled() {
        return config.getBoolean("port.enabled", super.isNonSecurePortEnabled());
    }

    public boolean getSecurePortEnabled() {
        return config.getBoolean("securePort.enabled", super.getSecurePortEnabled());
    }

    public int getLeaseRenewalIntervalInSeconds() {
        return config.getInteger("lease.renewalInterval", super.getLeaseRenewalIntervalInSeconds());
    }

    public int getLeaseExpirationDurationInSeconds() {
        return config.getInteger("lease.duration", super.getLeaseExpirationDurationInSeconds());
    }

    public String getVirtualHostName() {
        return this.isNonSecurePortEnabled()
            ? config.getString("vipAddress", super.getVirtualHostName())
            : null;
    }

    public String getSecureVirtualHostName() {
        return this.getSecurePortEnabled()
            ? config.getString("secureVipAddress", null)
            : null;
    }

    public String getASGName() {
        return config.getString("asgName", super.getASGName());
    }

    public Map<String, String> getMetadataMap() {
        Map<String, String> meta = new HashMap<>(); 
        Config config = this.config.getPrefixedView("metadata");
        for (String key : Sets.newHashSet(config.getKeys())) {
            meta.put(key, config.getString(key));
        }
        return meta;
    }

    public DataCenterInfo getDataCenterInfo() {
        return dcInfo;
    }

    public String getStatusPageUrlPath() {
        return config.getString("statusPageUrlPath", "/Status");
    }

    public String getStatusPageUrl() {
        return config.getString("statusPageUrl", null);
    }

    public String getHomePageUrlPath() {
        return config.getString("homePageUrlPath", "/");
    }

    public String getHomePageUrl() {
        return config.getString("homePageUrl", null);
    }

    public String getHealthCheckUrlPath() {
        return config.getString("healthCheckUrlPath", "/healthcheck");
    }

    public String getHealthCheckUrl() {
        return config.getString("healthCheckUr", null);
    }

    public String getSecureHealthCheckUrl() {
        return config.getString("secureHealthCheckUrl", null);
    }

    public String getNamespace() {
        return namespace;
    }
}

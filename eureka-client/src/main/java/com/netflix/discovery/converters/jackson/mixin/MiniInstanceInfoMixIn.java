package com.netflix.discovery.converters.jackson.mixin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;

/**
 * @author Tomasz Bak
 */
public interface MiniInstanceInfoMixIn {

    // define fields are are allowed for mini-InstanceInfo
    static class AllowedFields {
        public static final Set<String> ALLOWED_FIELDS;
        static {
            Set<String> fields = new HashSet<>();
            fields.add("sid");
            fields.add("app");
            fields.add("ipAddr");
            fields.add("vipAddress");
            fields.add("secureVipAddress");
            fields.add("dataCenterInfo");
            fields.add("hostName");
            fields.add("status");
            fields.add("actionType");
            fields.add("asgName");
            fields.add("lastUpdatedTimestamp");
            ALLOWED_FIELDS = fields;
        }
    }

    // define fields are are ignored for mini-InstanceInfo
    @JsonIgnore
    String getAppGroupName();

    @JsonIgnore
    InstanceStatus getOverriddenStatus();

    @JsonIgnore
    int getCountryId();

    @JsonIgnore
    String getHomePageUrl();

    @JsonIgnore
    String getStatusPageUrl();

    @JsonIgnore
    String getHealthCheckUrl();

    @JsonIgnore
    String getSecureHealthCheckUrl();

    @JsonIgnore
    boolean isCoordinatingDiscoveryServer();

    @JsonIgnore
    Long getLastDirtyTimestamp();

    @JsonIgnore
    LeaseInfo getLeaseInfo();

    @JsonIgnore
    Map<String, String> getMetadata();
}

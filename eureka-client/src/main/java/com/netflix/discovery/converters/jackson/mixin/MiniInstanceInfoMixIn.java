package com.netflix.discovery.converters.jackson.mixin;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;

/**
 * @author Tomasz Bak
 */
public interface MiniInstanceInfoMixIn {

    // define fields are are ignored for mini-InstanceInfo
    @JsonIgnore
    String getAppGroupName();

    @JsonIgnore
    InstanceStatus getOverriddenStatus();

    @JsonIgnore
    String getSID();

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

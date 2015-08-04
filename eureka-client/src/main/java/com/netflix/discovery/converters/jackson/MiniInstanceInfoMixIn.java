package com.netflix.discovery.converters.jackson;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;

/**
 * @author Tomasz Bak
 */
public interface MiniInstanceInfoMixIn {

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
    LeaseInfo getLeaseInfo();

    @JsonIgnore
    long getLastUpdatedTimestamp();

    @JsonIgnore
    Long getLastDirtyTimestamp();

    @JsonIgnore
    Map<String, String> getMetadata();
}

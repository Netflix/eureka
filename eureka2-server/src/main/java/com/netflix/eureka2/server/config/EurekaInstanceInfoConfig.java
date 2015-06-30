package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;

/**
 * @author Tomasz Bak
 */
public interface EurekaInstanceInfoConfig {

    String DEFAULT_EUREKA_APPLICATION_NAME = "defaultEurekaCluster";

    int DEFAULT_DATA_CENTER_RESOLVE_INTERVAL_SEC = 30;

    @DefaultValue(DEFAULT_EUREKA_APPLICATION_NAME)
    String getEurekaApplicationName();

    @DefaultValue(DEFAULT_EUREKA_APPLICATION_NAME)
    String getEurekaVipAddress();

    @DefaultValue("Basic")
    DataCenterType getDataCenterType();

    @DefaultValue("" + DEFAULT_DATA_CENTER_RESOLVE_INTERVAL_SEC)
    long getDataCenterResolveIntervalSec();
}

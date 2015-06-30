package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaInstanceInfoConfigBean implements EurekaInstanceInfoConfig {

    private final String eurekaApplicationName;
    private final String eurekaVipAddress;
    private final DataCenterType dataCenterType;
    private final long dataCenterResolveIntervalSec;

    public EurekaInstanceInfoConfigBean(String eurekaApplicationName, String eurekaVipAddress, DataCenterType dataCenterType, long dataCenterResolveIntervalSec) {
        this.eurekaApplicationName = eurekaApplicationName;
        this.eurekaVipAddress = eurekaVipAddress;
        this.dataCenterType = dataCenterType;
        this.dataCenterResolveIntervalSec = dataCenterResolveIntervalSec;
    }

    @Override
    public String getEurekaApplicationName() {
        return eurekaApplicationName;
    }

    @Override
    public String getEurekaVipAddress() {
        return eurekaVipAddress;
    }

    @Override
    public DataCenterType getDataCenterType() {
        return dataCenterType;
    }

    @Override
    public long getDataCenterResolveIntervalSec() {
        return dataCenterResolveIntervalSec;
    }

    public static Builder anEurekaInstanceInfoConfig() {
        return new Builder();
    }

    public static class Builder {
        private String eurekaApplicationName = DEFAULT_EUREKA_APPLICATION_NAME;
        private String eurekaVipAddress = DEFAULT_EUREKA_APPLICATION_NAME;
        private DataCenterType dataCenterType = DataCenterType.Basic;
        private long dataCenterResolveIntervalSec = DEFAULT_DATA_CENTER_RESOLVE_INTERVAL_SEC;

        private Builder() {
        }

        public Builder withEurekaApplicationName(String eurekaApplicationName) {
            this.eurekaApplicationName = eurekaApplicationName;
            return this;
        }

        public Builder withEurekaVipAddress(String eurekaVipAddress) {
            this.eurekaVipAddress = eurekaVipAddress;
            return this;
        }

        public Builder withDataCenterType(DataCenterType dataCenterType) {
            this.dataCenterType = dataCenterType;
            return this;
        }

        public Builder withDataCenterResolveIntervalSec(long dataCenterResolveIntervalSec) {
            this.dataCenterResolveIntervalSec = dataCenterResolveIntervalSec;
            return this;
        }

        public Builder but() {
            return anEurekaInstanceInfoConfig().withEurekaApplicationName(eurekaApplicationName).withEurekaVipAddress(eurekaVipAddress).withDataCenterType(dataCenterType).withDataCenterResolveIntervalSec(dataCenterResolveIntervalSec);
        }

        public EurekaInstanceInfoConfigBean build() {
            EurekaInstanceInfoConfigBean eurekaInstanceInfoConfigBean = new EurekaInstanceInfoConfigBean(eurekaApplicationName, eurekaVipAddress, dataCenterType, dataCenterResolveIntervalSec);
            return eurekaInstanceInfoConfigBean;
        }
    }
}

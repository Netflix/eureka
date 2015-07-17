package com.netflix.eureka2.server.config.bean;

import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;

/**
 * @author Tomasz Bak
 */
public class EurekaInstanceInfoConfigBean implements EurekaInstanceInfoConfig {

    private final String uniqueId;
    private final String eurekaApplicationName;
    private final String eurekaVipAddress;
    private final DataCenterType dataCenterType;
    private final long dataCenterResolveIntervalSec;

    public EurekaInstanceInfoConfigBean(String uniqueId,
                                        String eurekaApplicationName,
                                        String eurekaVipAddress,
                                        DataCenterType dataCenterType,
                                        long dataCenterResolveIntervalSec) {
        this.uniqueId = uniqueId;
        this.eurekaApplicationName = eurekaApplicationName;
        this.eurekaVipAddress = eurekaVipAddress;
        this.dataCenterType = dataCenterType;
        this.dataCenterResolveIntervalSec = dataCenterResolveIntervalSec;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
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
        private String uniqueId = null;
        private String eurekaApplicationName = DEFAULT_EUREKA_APPLICATION_NAME;
        private String eurekaVipAddress = DEFAULT_EUREKA_APPLICATION_NAME;
        private DataCenterType dataCenterType = DataCenterType.Basic;
        private long dataCenterResolveIntervalSec = DEFAULT_DATA_CENTER_RESOLVE_INTERVAL_SEC;

        private Builder() {
        }

        public Builder withUniqueId(String uniqueId) {
            this.uniqueId = uniqueId;
            return this;
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
            return anEurekaInstanceInfoConfig()
                    .withUniqueId(uniqueId)
                    .withEurekaApplicationName(eurekaApplicationName)
                    .withEurekaVipAddress(eurekaVipAddress)
                    .withDataCenterType(dataCenterType)
                    .withDataCenterResolveIntervalSec(dataCenterResolveIntervalSec);
        }

        public EurekaInstanceInfoConfigBean build() {
            EurekaInstanceInfoConfigBean eurekaInstanceInfoConfigBean = new EurekaInstanceInfoConfigBean(
                    uniqueId, eurekaApplicationName, eurekaVipAddress, dataCenterType, dataCenterResolveIntervalSec);
            return eurekaInstanceInfoConfigBean;
        }
    }
}

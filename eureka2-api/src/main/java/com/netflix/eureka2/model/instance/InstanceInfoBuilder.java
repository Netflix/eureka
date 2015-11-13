/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.instance;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;

/**
 */
public abstract class InstanceInfoBuilder {

    protected String id;

    protected String appGroup;
    protected String app;
    protected String asg;
    protected String vipAddress;
    protected String secureVipAddress;
    protected HashSet<ServicePort> ports;
    protected Status status;
    protected String homePageUrl;
    protected String statusPageUrl;
    protected HashSet<String> healthCheckUrls;
    protected DataCenterInfo dataCenterInfo;
    protected Map<String, String> metaData;

    public InstanceInfoBuilder withInstanceInfo(InstanceInfo another) {
        this.id = another.getId();

        this.appGroup = another.getAppGroup();
        this.app = another.getApp();
        this.asg = another.getAsg();
        this.vipAddress = another.getVipAddress();
        this.secureVipAddress = another.getSecureVipAddress();
        this.ports = (HashSet<ServicePort>) another.getPorts();
        this.status = another.getStatus();
        this.homePageUrl = another.getHomePageUrl();
        this.statusPageUrl = another.getStatusPageUrl();
        this.healthCheckUrls = another.getHealthCheckUrls();
        this.metaData = another.getMetaData() == null ? null : new HashMap<>(another.getMetaData());
        this.dataCenterInfo = another.getDataCenterInfo();
        return this;
    }

    public InstanceInfoBuilder withBuilder(InstanceInfoBuilder another) {
        this.id = another.id == null ? this.id : another.id;

        this.appGroup = another.appGroup == null ? this.appGroup : another.appGroup;
        this.app = another.app == null ? this.app : another.app;
        this.asg = another.asg == null ? this.asg : another.asg;
        this.vipAddress = another.vipAddress == null ? this.vipAddress : another.vipAddress;
        this.secureVipAddress = another.secureVipAddress == null ? this.secureVipAddress : another.secureVipAddress;
        this.ports = another.ports == null ? this.ports : new HashSet<>(another.ports);
        this.status = another.status == null ? this.status : another.status;
        this.homePageUrl = another.homePageUrl == null ? this.homePageUrl : another.homePageUrl;
        this.statusPageUrl = another.statusPageUrl == null ? this.statusPageUrl : another.statusPageUrl;
        this.healthCheckUrls = another.healthCheckUrls == null ? this.healthCheckUrls : new HashSet<>(another.healthCheckUrls);
        this.metaData = another.metaData == null ? this.metaData : new HashMap<>(another.metaData);
        this.dataCenterInfo = another.dataCenterInfo == null ? this.dataCenterInfo : another.dataCenterInfo;
        return this;
    }

    public InstanceInfoBuilder withId(String id) {
        this.id = id;
        return this;
    }

    public InstanceInfoBuilder withAppGroup(String appGroup) {
        this.appGroup = appGroup;
        return this;
    }

    public InstanceInfoBuilder withApp(String app) {
        this.app = app;
        return this;
    }

    public InstanceInfoBuilder withAsg(String asg) {
        this.asg = asg;
        return this;
    }

    public InstanceInfoBuilder withVipAddress(String vipAddress) {
        this.vipAddress = vipAddress;
        return this;
    }

    public InstanceInfoBuilder withSecureVipAddress(String secureVipAddress) {
        this.secureVipAddress = secureVipAddress;
        return this;
    }

    public InstanceInfoBuilder withPorts(HashSet<ServicePort> ports) {
        if (ports == null || ports.isEmpty()) {
            this.ports = null;
        } else {
            this.ports = new HashSet<>(ports);
            this.ports.remove(null);
        }
        return this;
    }

    public InstanceInfoBuilder withPorts(ServicePort... ports) {
        if (ports == null || ports.length == 0) {
            this.ports = null;
        }
        this.ports = new HashSet<>();
        Collections.addAll(this.ports, ports);
        return this;
    }

    public InstanceInfoBuilder withStatus(Status status) {
        this.status = status;
        return this;
    }

    public InstanceInfoBuilder withHomePageUrl(String homePageUrl) {
        this.homePageUrl = homePageUrl;
        return this;
    }

    public InstanceInfoBuilder withStatusPageUrl(String statusPageUrl) {
        this.statusPageUrl = statusPageUrl;
        return this;
    }

    public InstanceInfoBuilder withHealthCheckUrls(HashSet<String> healthCheckUrls) {
        if (healthCheckUrls == null || healthCheckUrls.isEmpty()) {
            this.healthCheckUrls = null;
        } else {
            this.healthCheckUrls = new HashSet<>(healthCheckUrls);
            this.healthCheckUrls.remove(null); // Data cleanup
        }
        return this;
    }

    public InstanceInfoBuilder withMetaData(String key, String value) {
        if (metaData == null) {
            metaData = new HashMap<>();
        }
        metaData.put(key, value);
        return this;
    }

    public InstanceInfoBuilder withMetaData(Map<String, String> metaData) {
        this.metaData = metaData;
        return this;
    }

    public InstanceInfoBuilder withDataCenterInfo(DataCenterInfo location) {
        this.dataCenterInfo = location;
        return this;
    }

    public abstract InstanceInfo build();
}

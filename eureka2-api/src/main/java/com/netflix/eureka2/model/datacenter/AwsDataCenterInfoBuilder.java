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

package com.netflix.eureka2.model.datacenter;

/**
 */
public abstract class AwsDataCenterInfoBuilder extends DataCenterInfoBuilder<AwsDataCenterInfo> {

    protected String region;
    protected String zone;
    protected String placementGroup;
    protected String amiId;
    protected String instanceId;
    protected String instanceType;
    protected String privateIP;
    protected String privateHostName;
    protected String publicIP;
    protected String publicHostName;
    protected String eth0mac;
    protected String vpcId;
    protected String accountId;

    public AwsDataCenterInfoBuilder withAwsDataCenter(AwsDataCenterInfo dataCenter) {
        this.region = dataCenter.getRegion();
        this.zone = dataCenter.getZone();
        this.placementGroup = dataCenter.getPlacementGroup();
        this.amiId = dataCenter.getAmiId();
        this.instanceId = dataCenter.getInstanceId();
        this.instanceType = dataCenter.getInstanceType();
        this.privateIP = dataCenter.getPrivateAddress().getIpAddress();
        this.privateHostName = dataCenter.getPrivateAddress().getHostName();
        this.publicIP = dataCenter.getPublicAddress().getIpAddress();
        this.publicHostName = dataCenter.getPublicAddress().getHostName();
        this.eth0mac = dataCenter.getEth0mac();
        this.vpcId = dataCenter.getVpcId();
        this.accountId = dataCenter.getAccountId();

        return this;
    }

    public AwsDataCenterInfoBuilder withRegion(String region) {
        this.region = region;
        return this;
    }

    public AwsDataCenterInfoBuilder withZone(String zone) {
        this.zone = zone;
        return this;
    }

    public AwsDataCenterInfoBuilder withPlacementGroup(String placementGroup) {
        this.placementGroup = placementGroup;
        return this;
    }

    public AwsDataCenterInfoBuilder withAmiId(String amiId) {
        this.amiId = amiId;
        return this;
    }

    public AwsDataCenterInfoBuilder withInstanceId(String instanceId) {
        this.instanceId = instanceId;
        return this;
    }

    public AwsDataCenterInfoBuilder withInstanceType(String instanceType) {
        this.instanceType = instanceType;
        return this;
    }

    public AwsDataCenterInfoBuilder withPrivateIPv4(String privateIP) {
        this.privateIP = privateIP;
        return this;
    }

    public AwsDataCenterInfoBuilder withPrivateHostName(String privateHostName) {
        this.privateHostName = privateHostName;
        return this;
    }

    public AwsDataCenterInfoBuilder withPublicIPv4(String publicIP) {
        this.publicIP = publicIP;
        return this;
    }

    public AwsDataCenterInfoBuilder withPublicHostName(String publicHostName) {
        this.publicHostName = publicHostName;
        return this;
    }

    public AwsDataCenterInfoBuilder withVpcId(String vpcId) {
        this.vpcId = vpcId;
        return this;
    }

    public AwsDataCenterInfoBuilder withAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public AwsDataCenterInfoBuilder withEth0mac(String eth0mac) {
        this.eth0mac = eth0mac;
        return this;
    }
}

/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.appinfo;

import com.google.inject.ProvidedBy;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.providers.CloudInstanceConfigProvider;
import com.netflix.discovery.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;

/**
 * An {@link InstanceInfo} configuration for AWS cloud deployments.
 *
 * <p>
 * The information required for registration with eureka by a combination of
 * user-supplied values as well as querying AWS instance metadata.An utility
 * class {@link AmazonInfo} helps in retrieving AWS specific values. Some of
 * that information including <em>availability zone</em> is used for determining
 * which eureka server to communicate to.
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
@Singleton
@ProvidedBy(CloudInstanceConfigProvider.class)
public class CloudInstanceConfig extends PropertiesInstanceConfig implements RefreshableInstanceConfig {
    private static final Logger logger = LoggerFactory.getLogger(CloudInstanceConfig.class);

    private static final String[] DEFAULT_AWS_ADDRESS_RESOLUTION_ORDER = new String[] {
            MetaDataKey.publicHostname.name(),
            MetaDataKey.localIpv4.name()
    };

    private final RefreshableAmazonInfoProvider amazonInfoHolder;

    public CloudInstanceConfig() {
        this(CommonConstants.DEFAULT_CONFIG_NAMESPACE);
    }

    public CloudInstanceConfig(String namespace) {
        this(namespace, new Archaius1AmazonInfoConfig(namespace), null, true);
    }

    /* visible for testing */ CloudInstanceConfig(AmazonInfo info) {
        this(CommonConstants.DEFAULT_CONFIG_NAMESPACE, new Archaius1AmazonInfoConfig(CommonConstants.DEFAULT_CONFIG_NAMESPACE), info, false);
    }

    /* visible for testing */ CloudInstanceConfig(String namespace, RefreshableAmazonInfoProvider refreshableAmazonInfoProvider) {
        super(namespace);
        this.amazonInfoHolder = refreshableAmazonInfoProvider;
    }

    /* visible for testing */ CloudInstanceConfig(String namespace, AmazonInfoConfig amazonInfoConfig, AmazonInfo initialInfo, boolean eagerInit) {
        super(namespace);
        if (eagerInit) {
            RefreshableAmazonInfoProvider.FallbackAddressProvider fallbackAddressProvider =
                    new RefreshableAmazonInfoProvider.FallbackAddressProvider() {
                        @Override
                        public String getFallbackIp() {
                            return CloudInstanceConfig.super.getIpAddress();
                        }

                        @Override
                        public String getFallbackHostname() {
                            return CloudInstanceConfig.super.getHostName(false);
                        }
            };
            this.amazonInfoHolder = new RefreshableAmazonInfoProvider(amazonInfoConfig, fallbackAddressProvider);
        } else {
            this.amazonInfoHolder = new RefreshableAmazonInfoProvider(initialInfo, amazonInfoConfig);
        }
    }

    /**
     * @deprecated use {@link #resolveDefaultAddress(boolean)}
     */
    @Deprecated
    public String resolveDefaultAddress() {
        return this.resolveDefaultAddress(true);
    }

    @Override
    public String resolveDefaultAddress(boolean refresh) {
        // In this method invocation data center info will be refreshed.
        String result = getHostName(refresh);

        for (String name : getDefaultAddressResolutionOrder()) {
            try {
                AmazonInfo.MetaDataKey key = AmazonInfo.MetaDataKey.valueOf(name);
                String address = amazonInfoHolder.get().get(key);
                if (address != null && !address.isEmpty()) {
                    result = address;
                    break;
                }
            } catch (Exception e) {
                logger.error("failed to resolve default address for key {}, skipping", name, e);
            }
        }

        return result;
    }

    @Override
    public String getHostName(boolean refresh) {
        if (refresh) {
            amazonInfoHolder.refresh();
        }
        return amazonInfoHolder.get().get(MetaDataKey.publicHostname);
    }

    @Override
    public String getIpAddress() {
        return this.shouldBroadcastPublicIpv4Addr() ?  getPublicIpv4Addr() : getPrivateIpv4Addr();
    }

    private String getPrivateIpv4Addr() {
        String privateIpv4Addr = amazonInfoHolder.get().get(MetaDataKey.localIpv4);
        return privateIpv4Addr == null ? super.getIpAddress() : privateIpv4Addr;
    }
    private String getPublicIpv4Addr() {
        String publicIpv4Addr = amazonInfoHolder.get().get(MetaDataKey.publicIpv4);
        return publicIpv4Addr == null ? super.getIpAddress() : publicIpv4Addr;
    }

    @Override
    public DataCenterInfo getDataCenterInfo() {
        return amazonInfoHolder.get();
    }

    @Override
    public String[] getDefaultAddressResolutionOrder() {
        String[] order = super.getDefaultAddressResolutionOrder();
        return (order.length == 0) ? DEFAULT_AWS_ADDRESS_RESOLUTION_ORDER : order;
    }

    /**
     * @deprecated 2016-09-07
     *
     * Refresh instance info - currently only used when in AWS cloud
     * as a public ip can change whenever an EIP is associated or dissociated.
     */
    @Deprecated
    public synchronized void refreshAmazonInfo() {
        amazonInfoHolder.refresh();
    }

    /**
     * @deprecated 2016-09-07
     */
    @Deprecated
    /* visible for testing */ static boolean shouldUpdate(AmazonInfo newInfo, AmazonInfo oldInfo) {
        return RefreshableAmazonInfoProvider.shouldUpdate(newInfo, oldInfo);
    }
}

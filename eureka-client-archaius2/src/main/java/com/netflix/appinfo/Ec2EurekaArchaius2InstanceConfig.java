package com.netflix.appinfo;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.netflix.discovery.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.archaius.api.Config;

/**
 * When running in EC2 add the following override binding.
 * 
 * 	bind(EurekaInstanceConfig.class).to(KaryonEc2EurekaInstanceConfig.class);
 * 
 * 
 * @author elandau
 *
 */
@Singleton
public class Ec2EurekaArchaius2InstanceConfig extends EurekaArchaius2InstanceConfig implements RefreshableInstanceConfig {
    private static final Logger LOG = LoggerFactory.getLogger(Ec2EurekaArchaius2InstanceConfig.class);

    private static final String[] DEFAULT_AWS_ADDRESS_RESOLUTION_ORDER = new String[] {
            MetaDataKey.publicHostname.name(),
            MetaDataKey.localIpv4.name()
    };

    private final AmazonInfoConfig amazonInfoConfig;
    private final Provider<AmazonInfo> amazonInfoHolder;

    @Inject
    public Ec2EurekaArchaius2InstanceConfig(Config configInstance, AmazonInfoConfig amazonInfoConfig) {
        this(configInstance, amazonInfoConfig, CommonConstants.DEFAULT_CONFIG_NAMESPACE);
    }

    /* visible for testing */ Ec2EurekaArchaius2InstanceConfig(Config configInstance, AmazonInfo info) {
        this(configInstance, new Archaius2AmazonInfoConfig(configInstance), CommonConstants.DEFAULT_CONFIG_NAMESPACE, info, false);
    }

    public Ec2EurekaArchaius2InstanceConfig(Config configInstance, Provider<AmazonInfo> amazonInfoProvider) {
        super(configInstance, CommonConstants.DEFAULT_CONFIG_NAMESPACE);
        this.amazonInfoConfig = null;
        this.amazonInfoHolder = amazonInfoProvider;
    }

    public Ec2EurekaArchaius2InstanceConfig(Config configInstance, AmazonInfoConfig amazonInfoConfig, String namespace) {
        this(configInstance, amazonInfoConfig, namespace, null, true);
    }

    /* visible for testing */ Ec2EurekaArchaius2InstanceConfig(Config configInstance,
                                                               AmazonInfoConfig amazonInfoConfig,
                                                               String namespace,
                                                               AmazonInfo initialInfo,
                                                               boolean eagerInit) {
        super(configInstance, namespace);
        this.amazonInfoConfig = amazonInfoConfig;

        if (eagerInit) {
            RefreshableAmazonInfoProvider.FallbackAddressProvider fallbackAddressProvider =
                    new RefreshableAmazonInfoProvider.FallbackAddressProvider() {
                        @Override
                        public String getFallbackIp() {
                            return Ec2EurekaArchaius2InstanceConfig.super.getIpAddress();
                        }

                        @Override
                        public String getFallbackHostname() {
                            return Ec2EurekaArchaius2InstanceConfig.super.getHostName(false);
                        }
                    };
            this.amazonInfoHolder = new RefreshableAmazonInfoProvider(amazonInfoConfig, fallbackAddressProvider);
        } else {
            this.amazonInfoHolder = new RefreshableAmazonInfoProvider(initialInfo, amazonInfoConfig);
        }
    }
    
    @Override
    public String getHostName(boolean refresh) {
        if (refresh && this.amazonInfoHolder instanceof RefreshableAmazonInfoProvider) {
            ((RefreshableAmazonInfoProvider)amazonInfoHolder).refresh();
        }
        return amazonInfoHolder.get().get(MetaDataKey.publicHostname);
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
        if (this.amazonInfoHolder instanceof RefreshableAmazonInfoProvider) {
            ((RefreshableAmazonInfoProvider)amazonInfoHolder).refresh();
        }
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
                LOG.error("failed to resolve default address for key {}, skipping", name, e);
            }
        }

        return result;
    }
}

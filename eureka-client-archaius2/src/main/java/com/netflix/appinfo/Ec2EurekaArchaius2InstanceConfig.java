package com.netflix.appinfo;

import javax.inject.Inject;
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
public class Ec2EurekaArchaius2InstanceConfig extends EurekaArchaius2InstanceConfig {
    private static final Logger LOG = LoggerFactory.getLogger(Ec2EurekaArchaius2InstanceConfig.class);

    private static final String[] DEFAULT_AWS_ADDRESS_RESOLUTION_ORDER = new String[] {
            MetaDataKey.publicHostname.name(),
            MetaDataKey.localIpv4.name()
    };

    private final AmazonInfoConfig amazonInfoConfig;
    private final RefreshableAmazonInfoProvider amazonInfoHolder;

    @Inject
    public Ec2EurekaArchaius2InstanceConfig(Config config, AmazonInfoConfig amazonInfoConfig) {
        this(config, amazonInfoConfig, CommonConstants.DEFAULT_CONFIG_NAMESPACE);
    }

    public Ec2EurekaArchaius2InstanceConfig(Config config, AmazonInfoConfig amazonInfoConfig, String namespace) {
        super(config, namespace);
        this.amazonInfoConfig = amazonInfoConfig;

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
    }
    
    @Override
    public String getHostName(boolean refresh) {
        if (refresh) {
            amazonInfoHolder.refresh();
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
        amazonInfoHolder.refresh();
    }
}

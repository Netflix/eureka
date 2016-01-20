package com.netflix.discovery;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.DataCenterInfo.Name;
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

    private volatile AmazonInfo amazonInfo;

    @Inject
    public Ec2EurekaArchaius2InstanceConfig(Config config) {
        this(config, DEFAULT_NAMESPACE);
    }

    public Ec2EurekaArchaius2InstanceConfig(Config config, String namespace) {
        super(config, namespace);
        
        try {
            this.amazonInfo = AmazonInfo.Builder.newBuilder().autoBuild(namespace);
            LOG.info("Datacenter is: " + Name.Amazon);
        } 
        catch (Exception e) {
            LOG.error("Cannot initialize amazon info :", e);
            throw new RuntimeException(e);
        }
        
        // Instance id being null means we could not get the amazon metadata
        if (amazonInfo.get(MetaDataKey.instanceId) == null) {
            if (config.getBoolean(namespace + ".validateInstanceId", true)) {
                throw new RuntimeException(
                        "Your datacenter is defined as cloud but we are not able to get the amazon metadata to "
                                + "register. \nSet the property 'eureka.validateInstanceId' to false to ignore the"
                                + "metadata call");
            } 
            else {
                // The property to not validate instance ids may be set for development and in that scenario,
                // populate instance id and public hostname with the hostname of the machine
                Map<String, String> metadataMap = new HashMap<>();
                metadataMap.put(MetaDataKey.instanceId.getName(),     super.getIpAddress());
                metadataMap.put(MetaDataKey.publicHostname.getName(), super.getHostName(false));
                amazonInfo.setMetadata(metadataMap);
            }
        } 
        else if ((amazonInfo.get(MetaDataKey.publicHostname) == null)
                && (amazonInfo.get(MetaDataKey.localIpv4) != null)) {
            // This might be a case of VPC where the instance id is not null, but
            // public hostname might be null
            amazonInfo.getMetadata().put(MetaDataKey.publicHostname.getName(),
                    (amazonInfo.get(MetaDataKey.localIpv4)));
        }        
    }
    
    @Override
    public String getHostName(boolean refresh) {
        if (refresh) {
            refreshAmazonInfo();
        }
        return amazonInfo.get(MetaDataKey.publicHostname);
    }

    @Override
    public DataCenterInfo getDataCenterInfo() {
        return amazonInfo;
    }

    @Override
    public String[] getDefaultAddressResolutionOrder() {
        String[] order = super.getDefaultAddressResolutionOrder();
        return (order.length == 0) ? DEFAULT_AWS_ADDRESS_RESOLUTION_ORDER : order;
    }

    /**
     * Refresh instance info - currently only used when in AWS cloud
     * as a public ip can change whenever an EIP is associated or dissociated.
     */
    public synchronized void refreshAmazonInfo() {
        try {
            AmazonInfo newInfo = AmazonInfo.Builder.newBuilder().autoBuild(DEFAULT_NAMESPACE);
            if (!newInfo.equals(amazonInfo)) {
                // the datacenter info has changed, re-sync it
                LOG.warn("The AmazonInfo changed from : {} => {}", amazonInfo, newInfo);
                this.amazonInfo = newInfo;
            }
        }
        catch (Exception e) {
            LOG.error("Cannot refresh the Amazon Info ", e);
        }
    }
}

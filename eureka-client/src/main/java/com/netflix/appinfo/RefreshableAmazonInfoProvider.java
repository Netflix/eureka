package com.netflix.appinfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A holder class for AmazonInfo that exposes some APIs to allow for refreshes.
 */
public class RefreshableAmazonInfoProvider implements Provider<AmazonInfo> {

    /**
     * A fallback provider for a default set of IP and hostname if equivalent data are not available
     * from the EC2 metadata url.
     */
    public static interface FallbackAddressProvider {
        String getFallbackIp();
        String getFallbackHostname();
    }


    private static final Logger logger = LoggerFactory.getLogger(RefreshableAmazonInfoProvider.class);

    /* Visible for testing */ volatile AmazonInfo info;
    private final AmazonInfoConfig amazonInfoConfig;

    public RefreshableAmazonInfoProvider(AmazonInfoConfig amazonInfoConfig, FallbackAddressProvider fallbackAddressProvider) {
        this(init(amazonInfoConfig, fallbackAddressProvider), amazonInfoConfig);
    }

    /* visible for testing */ RefreshableAmazonInfoProvider(AmazonInfo initialInfo, AmazonInfoConfig amazonInfoConfig) {
        this.amazonInfoConfig = amazonInfoConfig;
        this.info = initialInfo;
    }

    private static AmazonInfo init(AmazonInfoConfig amazonInfoConfig, FallbackAddressProvider fallbackAddressProvider) {
        AmazonInfo info;
        try {
            info = AmazonInfo.Builder
                    .newBuilder()
                    .withAmazonInfoConfig(amazonInfoConfig)
                    .autoBuild(amazonInfoConfig.getNamespace());
            logger.info("Datacenter is: {}", DataCenterInfo.Name.Amazon);
        } catch (Throwable e) {
            logger.error("Cannot initialize amazon info :", e);
            throw new RuntimeException(e);
        }
        // Instance id being null means we could not get the amazon metadata
        if (info.get(AmazonInfo.MetaDataKey.instanceId) == null) {
            if (amazonInfoConfig.shouldValidateInstanceId()) {
                throw new RuntimeException(
                        "Your datacenter is defined as cloud but we are not able to get the amazon metadata to "
                                + "register. \nSet the property " + amazonInfoConfig.getNamespace()
                                + "validateInstanceId to false to ignore the metadata call");
            } else {
                // The property to not validate instance ids may be set for
                // development and in that scenario, populate instance id
                // and public hostname with the hostname of the machine
                Map<String, String> metadataMap = new HashMap<String, String>();
                metadataMap.put(AmazonInfo.MetaDataKey.instanceId.getName(), fallbackAddressProvider.getFallbackIp());
                metadataMap.put(AmazonInfo.MetaDataKey.publicHostname.getName(), fallbackAddressProvider.getFallbackHostname());
                info.setMetadata(metadataMap);
            }
        } else if ((info.get(AmazonInfo.MetaDataKey.publicHostname) == null)
                && (info.get(AmazonInfo.MetaDataKey.localIpv4) != null)) {
            // :( legacy code and logic
            // This might be a case of VPC where the instance id is not null, but
            // public hostname might be null
            info.getMetadata().put(AmazonInfo.MetaDataKey.publicHostname.getName(), (info.get(AmazonInfo.MetaDataKey.localIpv4)));
        }
        return info;
    }

    /**
     * Refresh the locally held version of {@link com.netflix.appinfo.AmazonInfo}
     */
    public synchronized void refresh() {
        try {
            AmazonInfo newInfo = getNewAmazonInfo();

            if (shouldUpdate(newInfo, info)) {
                // the datacenter info has changed, re-sync it
                logger.info("The AmazonInfo changed from : {} => {}", info, newInfo);
                this.info = newInfo;
            }
        } catch (Throwable t) {
            logger.error("Cannot refresh the Amazon Info ", t);
        }
    }

    /* visible for testing */ AmazonInfo getNewAmazonInfo() {
        return AmazonInfo.Builder
                        .newBuilder()
                        .withAmazonInfoConfig(amazonInfoConfig)
                        .autoBuild(amazonInfoConfig.getNamespace());
    }

    /**
     * @return the locally held version of {@link com.netflix.appinfo.AmazonInfo}
     */
    @Override
    public AmazonInfo get() {
        return info;
    }


    /**
     * Rules of updating AmazonInfo:
     * - instanceId must exist
     * - localIp/privateIp must exist
     * - publicHostname does not necessarily need to exist (e.g. in vpc)
     */
    /* visible for testing */ static boolean shouldUpdate(AmazonInfo newInfo, AmazonInfo oldInfo) {
        if (newInfo.getMetadata().isEmpty()) {
            logger.warn("Newly resolved AmazonInfo is empty, skipping an update cycle");
        } else if (!newInfo.equals(oldInfo)) {
            if (isBlank(newInfo.get(AmazonInfo.MetaDataKey.instanceId))) {
                logger.warn("instanceId is blank, skipping an update cycle");
                return false;
            } else if (isBlank(newInfo.get(AmazonInfo.MetaDataKey.localIpv4))) {
                logger.warn("localIpv4 is blank, skipping an update cycle");
                return false;
            } else {
                Set<String> newKeys = new HashSet<>(newInfo.getMetadata().keySet());
                Set<String> oldKeys = new HashSet<>(oldInfo.getMetadata().keySet());

                Set<String> union = new HashSet<>(newKeys);
                union.retainAll(oldKeys);
                newKeys.removeAll(union);
                oldKeys.removeAll(union);

                for (String key : newKeys) {
                    logger.info("Adding new metadata {}={}", key, newInfo.getMetadata().get(key));
                }

                for (String key : oldKeys) {
                    logger.info("Removing old metadata {}={}", key, oldInfo.getMetadata().get(key));
                }
            }

            return true;
        }
        return false;
    }

    private static boolean isBlank(String str) {
        return str == null || str.isEmpty();
    }
}

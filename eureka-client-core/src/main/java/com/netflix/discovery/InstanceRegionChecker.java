package com.netflix.discovery;

import javax.annotation.Nullable;
import java.util.Map;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Nitesh Kant
 */
public class InstanceRegionChecker {
    private static Logger logger = LoggerFactory.getLogger(InstanceRegionChecker.class);

    private final AzToRegionMapper azToRegionMapper;
    private final String localRegion;

    InstanceRegionChecker(AzToRegionMapper azToRegionMapper, String localRegion) {
        this.azToRegionMapper = azToRegionMapper;
        this.localRegion = localRegion;
    }

    @Nullable
    public String getInstanceRegion(InstanceInfo instanceInfo) {
        if (instanceInfo.getDataCenterInfo() == null || instanceInfo.getDataCenterInfo().getName() == null) {
            logger.warn("Cannot get region for instance id:{}, app:{} as dataCenterInfo is null. Returning local:{} by default",
                    instanceInfo.getId(), instanceInfo.getAppName(), localRegion);

            return localRegion;
        }
        if (DataCenterInfo.Name.Amazon.equals(instanceInfo.getDataCenterInfo().getName())) {
            AmazonInfo amazonInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();
            Map<String, String> metadata = amazonInfo.getMetadata();
            String availabilityZone = metadata.get(AmazonInfo.MetaDataKey.availabilityZone.getName());
            if (null != availabilityZone) {
                return azToRegionMapper.getRegionForAvailabilityZone(availabilityZone);
            }
        }

        return null;
    }

    public boolean isLocalRegion(@Nullable String instanceRegion) {
        return null == instanceRegion || instanceRegion.equals(localRegion); // no region == local
    }

    public String getLocalRegion() {
        return localRegion;
    }

    public AzToRegionMapper getAzToRegionMapper() {
        return azToRegionMapper;
    }
}

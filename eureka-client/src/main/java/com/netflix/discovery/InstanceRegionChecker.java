package com.netflix.discovery;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.discovery.DefaultEurekaClientConfig.DEFAULT_ZONE;

/**
 * @author Nitesh Kant
 */
public class InstanceRegionChecker {

    private static final Logger logger = LoggerFactory.getLogger(InstanceRegionChecker.class);

    private final Map<String, String> availabilityZoneVsRegion = new HashMap<String, String>();
    private final String localRegion;

    InstanceRegionChecker(String remoteRegionsToFetch, EurekaClientConfig clientConfig) {
        localRegion = clientConfig.getRegion();
        if (null != remoteRegionsToFetch) {
            String[] remoteRegions = remoteRegionsToFetch.split(",");
            for (String remoteRegion : remoteRegions) {
                String[] availabilityZones = clientConfig.getAvailabilityZones(remoteRegion);
                if (null == availabilityZones ||
                    (availabilityZones.length == 1 && availabilityZones[0].equals(DEFAULT_ZONE))) {
                    String msg = "No availability zone information available for remote region: " + remoteRegion +
                                 ". This is required if registry information for this region is configured to be fetched.";
                    logger.error(msg);
                    throw new RuntimeException(msg);
                } else {
                    for (String availabilityZone : availabilityZones) {
                        availabilityZoneVsRegion.put(availabilityZone, remoteRegion);
                    }
                }
            }
        }
    }


    @Nullable
    public String getInstanceRegion(InstanceInfo instanceInfo) {
        if (DataCenterInfo.Name.Amazon.equals(instanceInfo.getDataCenterInfo().getName())) {
            Map<String, String> metadata = instanceInfo.getMetadata();
            String availabilityZone = metadata.get(AmazonInfo.MetaDataKey.availabilityZone.getName());
            if (null != availabilityZone) {
                return availabilityZoneVsRegion.get(availabilityZone);
            }
        }

        return null;
    }

    public boolean isInstanceInLocalRegion(InstanceInfo instanceInfo) {
        String instanceRegion = getInstanceRegion(instanceInfo);
        return isLocalRegion(instanceRegion);
    }

    public boolean isLocalRegion(@Nullable String instanceRegion) {
        return null == instanceRegion || instanceRegion.equals(localRegion); // no region == local
    }

    public String getLocalRegion() {
        return localRegion;
    }
}

package com.netflix.discovery;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.discovery.DefaultEurekaClientConfig.DEFAULT_ZONE;

/**
 * @author Nitesh Kant
 */
public class InstanceRegionChecker {

    private static final Logger logger = LoggerFactory.getLogger(InstanceRegionChecker.class);

    /**
     * A default for the mapping that we know of, if a remote region is configured to be fetched but does not have
     * any availability zone mapping, we will use these defaults. OTOH, if the remote region has any mapping defaults
     * will not be used.
     */
    private final Multimap<String, String> defaultRegionVsAzMap =
            Multimaps.newListMultimap(new HashMap<String, Collection<String>>(), new Supplier<List<String>>() {
                @Override
                public List<String> get() {
                    return new ArrayList<String>();
                }
            });

    private final Map<String, String> availabilityZoneVsRegion = new HashMap<String, String>();
    private final String localRegion;

    InstanceRegionChecker(String remoteRegionsToFetch, EurekaClientConfig clientConfig) {
        populateDefaultAZToRegionMap();
        localRegion = clientConfig.getRegion();
        if (null != remoteRegionsToFetch) {
            String[] remoteRegions = remoteRegionsToFetch.split(",");
            for (String remoteRegion : remoteRegions) {
                String[] availabilityZones = clientConfig.getAvailabilityZones(remoteRegion);
                if (null == availabilityZones ||
                    (availabilityZones.length == 1 && availabilityZones[0].equals(DEFAULT_ZONE))) {
                    logger.info("No availability zone information available for remote region: " + remoteRegion +
                                " via config. Now checking in the default mapping.");
                    if (defaultRegionVsAzMap.containsKey(remoteRegion)) {
                        Collection<String> defaultAvailabilityZones = defaultRegionVsAzMap.get(remoteRegion);
                        for (String defaultAvailabilityZone : defaultAvailabilityZones) {
                            availabilityZoneVsRegion.put(defaultAvailabilityZone, remoteRegion);
                        }
                    } else {
                        String msg = "No availability zone information available for remote region: " + remoteRegion +
                                     ". This is required if registry information for this region is configured to be fetched.";
                        logger.error(msg);
                        throw new RuntimeException(msg);
                    }
                } else {
                    for (String availabilityZone : availabilityZones) {
                        availabilityZoneVsRegion.put(availabilityZone, remoteRegion);
                    }
                }
            }

            logger.info("Availability zone to region mapping for all remote regions: {}", availabilityZoneVsRegion);
        }
    }

    private void populateDefaultAZToRegionMap() {
        defaultRegionVsAzMap.put("us-east-1", "us-east-1a");
        defaultRegionVsAzMap.put("us-east-1", "us-east-1c");
        defaultRegionVsAzMap.put("us-east-1", "us-east-1d");
        defaultRegionVsAzMap.put("us-east-1", "us-east-1e");

        defaultRegionVsAzMap.put("us-west-1", "us-west-1a");
        defaultRegionVsAzMap.put("us-west-1", "us-west-1c");

        defaultRegionVsAzMap.put("us-west-2", "us-west-2a");
        defaultRegionVsAzMap.put("us-west-2", "us-west-2b");
        defaultRegionVsAzMap.put("us-west-2", "us-west-2c");

        defaultRegionVsAzMap.put("eu-west-1", "eu-west-1a");
        defaultRegionVsAzMap.put("eu-west-1", "eu-west-1b");
        defaultRegionVsAzMap.put("eu-west-1", "eu-west-1c");
    }


    @Nullable
    public String getInstanceRegion(InstanceInfo instanceInfo) {
        if (DataCenterInfo.Name.Amazon.equals(instanceInfo.getDataCenterInfo().getName())) {
            AmazonInfo amazonInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();
            Map<String, String> metadata = amazonInfo.getMetadata();
            String availabilityZone = metadata.get(AmazonInfo.MetaDataKey.availabilityZone.getName());
            if (null != availabilityZone) {
                return availabilityZoneVsRegion.get(availabilityZone);
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
}

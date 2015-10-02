package com.netflix.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.DefaultEurekaClientConfig.DEFAULT_ZONE;

/**
 * @author Nitesh Kant
 */
public abstract class AbstractAzToRegionMapper implements AzToRegionMapper {

    private static final Logger logger = LoggerFactory.getLogger(InstanceRegionChecker.class);
    private static final String[] EMPTY_STR_ARRAY = new String[0];

    protected final EurekaClientConfig clientConfig;

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

    private final Map<String, String> availabilityZoneVsRegion = new ConcurrentHashMap<String, String>();
    private String[] regionsToFetch;

    protected AbstractAzToRegionMapper(EurekaClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        populateDefaultAZToRegionMap();
    }

    @Override
    public synchronized void setRegionsToFetch(String[] regionsToFetch) {
        if (null != regionsToFetch) {
            this.regionsToFetch = regionsToFetch;
            logger.info("Fetching availability zone to region mapping for regions {}", Arrays.toString(regionsToFetch));
            availabilityZoneVsRegion.clear();
            for (String remoteRegion : regionsToFetch) {
                Set<String> availabilityZones = getZonesForARegion(remoteRegion);
                if (null == availabilityZones
                        || (availabilityZones.size() == 1 && availabilityZones.iterator().next().equals(DEFAULT_ZONE))
                        || availabilityZones.isEmpty()) {
                    logger.info("No availability zone information available for remote region: " + remoteRegion
                            + ". Now checking in the default mapping.");
                    if (defaultRegionVsAzMap.containsKey(remoteRegion)) {
                        Collection<String> defaultAvailabilityZones = defaultRegionVsAzMap.get(remoteRegion);
                        for (String defaultAvailabilityZone : defaultAvailabilityZones) {
                            availabilityZoneVsRegion.put(defaultAvailabilityZone, remoteRegion);
                        }
                    } else {
                        String msg = "No availability zone information available for remote region: " + remoteRegion
                                + ". This is required if registry information for this region is configured to be "
                                + "fetched.";
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
        } else {
            logger.info("Regions to fetch is null. Erasing older mapping if any.");
            availabilityZoneVsRegion.clear();
            this.regionsToFetch = EMPTY_STR_ARRAY;
        }
    }

    /**
     * Returns all the zones in the provided region.
     * @param region the region whose zones you want
     * @return a set of zones
     */
    protected abstract Set<String> getZonesForARegion(String region);

    @Override
    public String getRegionForAvailabilityZone(String availabilityZone) {
        String region = availabilityZoneVsRegion.get(availabilityZone);
        if (null == region) {
            return parseAzToGetRegion(availabilityZone);
        }
        return region;
    }

    @Override
    public synchronized void refreshMapping() {
        logger.info("Refreshing availability zone to region mappings.");
        setRegionsToFetch(regionsToFetch);
    }

    /**
     * Tries to determine what region we're in, based on the provided availability zone.
     * @param availabilityZone the availability zone to inspect
     * @return the region, if available; null otherwise
     */
    protected String parseAzToGetRegion(String availabilityZone) {
        // Here we see that whether the availability zone is following a pattern like <region><single letter>
        // If it is then we take ignore the last letter and check if the remaining part is actually a known remote
        // region. If yes, then we return that region, else null which means local region.
        if (!availabilityZone.isEmpty()) {
            String possibleRegion = availabilityZone.substring(0, availabilityZone.length() - 1);
            if (availabilityZoneVsRegion.containsValue(possibleRegion)) {
                return possibleRegion;
            }
        }
        return null;
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
}

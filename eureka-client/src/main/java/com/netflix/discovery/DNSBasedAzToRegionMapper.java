package com.netflix.discovery;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Nitesh Kant
 */
public class DNSBasedAzToRegionMapper extends AbstractAzToRegionMapper {

    @Override
    protected Set<String> getZonesForARegion(String region) {
        Map<String, List<String>> zoneBasedDiscoveryUrlsFromRegion =
                DiscoveryClient.getZoneBasedDiscoveryUrlsFromRegion(region);
        if (null != zoneBasedDiscoveryUrlsFromRegion) {
            return zoneBasedDiscoveryUrlsFromRegion.keySet();
        }

        return Collections.emptySet();
    }
}

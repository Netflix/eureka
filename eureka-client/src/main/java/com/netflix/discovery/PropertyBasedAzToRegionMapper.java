package com.netflix.discovery;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Nitesh Kant
 */
public class PropertyBasedAzToRegionMapper extends AbstractAzToRegionMapper {

    private final EurekaClientConfig clientConfig;

    public PropertyBasedAzToRegionMapper(EurekaClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Override
    protected Set<String> getZonesForARegion(String region) {
        return new HashSet<String>(Arrays.asList(clientConfig.getAvailabilityZones(region)));
    }
}

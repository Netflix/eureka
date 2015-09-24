package com.netflix.discovery;

import com.netflix.discovery.endpoint.EndpointUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DNS-based region mapper that discovers regions via DNS TXT records.
 * @author Nitesh Kant
 */
public class DNSBasedAzToRegionMapper extends AbstractAzToRegionMapper {

    public DNSBasedAzToRegionMapper(EurekaClientConfig clientConfig) {
        super(clientConfig);
    }

    @Override
    protected Set<String> getZonesForARegion(String region) {
        Map<String, List<String>> zoneBasedDiscoveryUrlsFromRegion = EndpointUtils
                .getZoneBasedDiscoveryUrlsFromRegion(clientConfig, region);
        if (null != zoneBasedDiscoveryUrlsFromRegion) {
            return zoneBasedDiscoveryUrlsFromRegion.keySet();
        }

        return Collections.emptySet();
    }
}

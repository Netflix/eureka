package com.netflix.discovery;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netflix.discovery.util.DnsResolver;

/**
 * DNS-based region mapper that discovers regions via DNS TXT records.
 * @author Nitesh Kant
 */
public class DNSBasedAzToRegionMapper extends AbstractAzToRegionMapper {

    private final DnsResolver dnsResolver;

    public DNSBasedAzToRegionMapper(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    @Override
    protected Set<String> getZonesForARegion(String region) {
        Map<String, List<String>> zoneBasedDiscoveryUrlsFromRegion =
                DiscoveryClient.getZoneBasedDiscoveryUrlsFromRegion(dnsResolver, region);
        if (null != zoneBasedDiscoveryUrlsFromRegion) {
            return zoneBasedDiscoveryUrlsFromRegion.keySet();
        }

        return Collections.emptySet();
    }
}

package com.netflix.discovery.shared.resolver.aws;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.endpoint.EndpointUtils;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A resolver that on-demand resolves from configuration what the endpoints should be.
 *
 * @author David Liu
 */
public class ConfigClusterResolver implements ClusterResolver<AwsEndpoint> {
    private static final Logger logger = LoggerFactory.getLogger(ConfigClusterResolver.class);

    private final EurekaClientConfig clientConfig;
    private final InstanceInfo myInstanceInfo;

    public ConfigClusterResolver(EurekaClientConfig clientConfig, InstanceInfo myInstanceInfo) {
        this.clientConfig = clientConfig;
        this.myInstanceInfo = myInstanceInfo;
    }

    @Override
    public String getRegion() {
        return clientConfig.getRegion();
    }

    @Override
    public List<AwsEndpoint> getClusterEndpoints() {
        if (clientConfig.shouldUseDnsForFetchingServiceUrls()) {
            if (logger.isInfoEnabled()) {
                logger.info("Resolving eureka endpoints via DNS: {}", getDNSName());
            }
            return getClusterEndpointsFromDns();
        } else {
            logger.info("Resolving eureka endpoints via configuration");
            return getClusterEndpointsFromConfig();
        }
    }

    private List<AwsEndpoint> getClusterEndpointsFromDns() {
        String discoveryDnsName = getDNSName();
        int port = Integer.parseInt(clientConfig.getEurekaServerPort());

        // cheap enough so just re-use
        DnsTxtRecordClusterResolver dnsResolver = new DnsTxtRecordClusterResolver(
                getRegion(),
                discoveryDnsName,
                true,
                port,
                false,
                clientConfig.getEurekaServerURLContext()
        );

        List<AwsEndpoint> endpoints = dnsResolver.getClusterEndpoints();

        if (endpoints.isEmpty()) {
            logger.error("Cannot resolve to any endpoints for the given dnsName: {}", discoveryDnsName);
        }

        return endpoints;
    }

    private List<AwsEndpoint> getClusterEndpointsFromConfig() {
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        String myZone = InstanceInfo.getZone(availZones, myInstanceInfo);

        Map<String, List<String>> serviceUrls = EndpointUtils
                .getServiceUrlsMapFromConfig(clientConfig, myZone, clientConfig.shouldPreferSameZoneEureka());

        List<AwsEndpoint> endpoints = new ArrayList<>();
        for (String zone : serviceUrls.keySet()) {
            for (String url : serviceUrls.get(zone)) {
                try {
                    endpoints.add(new AwsEndpoint(url, getRegion(), zone));
                } catch (Exception ignore) {
                    logger.warn("Invalid eureka server URI: {}; removing from the server pool", url);
                }
            }
        }

        logger.debug("Config resolved to {}", endpoints);

        if (endpoints.isEmpty()) {
            logger.error("Cannot resolve to any endpoints from provided configuration: {}", serviceUrls);
        }

        return endpoints;
    }

    private String getDNSName() {
        return "txt." + getRegion() + '.' + clientConfig.getEurekaServerDNSName();
    }
}

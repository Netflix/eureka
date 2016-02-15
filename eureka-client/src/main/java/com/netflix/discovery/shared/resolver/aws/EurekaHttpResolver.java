package com.netflix.discovery.shared.resolver.aws;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaClientNames;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.ResolverUtils;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author David Liu
 */
public class EurekaHttpResolver implements ClusterResolver<AwsEndpoint> {
    private static final Logger logger = LoggerFactory.getLogger(EurekaHttpResolver.class);

    private final EurekaClientConfig clientConfig;
    private final EurekaTransportConfig transportConfig;
    private final String vipAddress;
    private final EurekaHttpClientFactory clientFactory;

    public EurekaHttpResolver(EurekaClientConfig clientConfig,
                              EurekaTransportConfig transportConfig,
                              ClusterResolver<EurekaEndpoint> bootstrapResolver,
                              TransportClientFactory transportClientFactory,
                              String vipAddress) {
        this(
                clientConfig,
                transportConfig,
                RetryableEurekaHttpClient.createFactory(
                        EurekaClientNames.RESOLVER,
                        transportConfig,
                        bootstrapResolver,
                        transportClientFactory,
                        ServerStatusEvaluators.httpSuccessEvaluator()
                ),
                vipAddress
        );
    }

    /* visible for testing */ EurekaHttpResolver(EurekaClientConfig clientConfig,
                                                 EurekaTransportConfig transportConfig,
                                                 EurekaHttpClientFactory clientFactory,
                                                 String vipAddress) {
        this.clientConfig = clientConfig;
        this.transportConfig = transportConfig;
        this.clientFactory = clientFactory;
        this.vipAddress = vipAddress;
    }

    @Override
    public String getRegion() {
        return clientConfig.getRegion();
    }

    @Override
    public List<AwsEndpoint> getClusterEndpoints() {
        List<AwsEndpoint> result = new ArrayList<>();

        EurekaHttpClient client = null;
        try {
            client = clientFactory.newClient();
            EurekaHttpResponse<Applications> response = client.getVip(vipAddress);
            if (validResponse(response)) {
                Applications applications = response.getEntity();
                if (applications != null) {
                    applications.shuffleInstances(true);  // filter out non-UP instances
                    List<InstanceInfo> validInstanceInfos = applications.getInstancesByVirtualHostName(vipAddress);
                    for (InstanceInfo instanceInfo : validInstanceInfos) {
                        AwsEndpoint endpoint = ResolverUtils.instanceInfoToEndpoint(clientConfig, transportConfig, instanceInfo);
                        if (endpoint != null) {
                            result.add(endpoint);
                        }
                    }
                    logger.debug("Retrieved endpoint list {}", result);
                    return result;
                }
            }
        } catch (Exception e) {
            logger.error("Error contacting server for endpoints with vipAddress:{}", vipAddress, e);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }

        logger.info("Returning empty endpoint list");
        return Collections.emptyList();
    }

    private <T> boolean validResponse(EurekaHttpResponse<T> response) {
        if (response == null) {
            return false;
        }

        int responseCode = response.getStatusCode();
        return responseCode >= 200 && responseCode < 300;
    }
}

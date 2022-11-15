package com.netflix.eureka.transport;

import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.resources.ServerCodecs;

public interface EurekaServerHttpClientFactory {

	EurekaHttpClient createRemoteRegionClient(EurekaServerConfig serverConfig,
											  EurekaTransportConfig transportConfig,
											  ServerCodecs serverCodecs,
											  ClusterResolver<EurekaEndpoint> clusterResolver);

}

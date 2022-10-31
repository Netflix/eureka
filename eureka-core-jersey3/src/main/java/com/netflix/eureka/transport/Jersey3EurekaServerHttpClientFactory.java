package com.netflix.eureka.transport;

import com.netflix.discovery.shared.dns.DnsServiceImpl;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RedirectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.discovery.shared.transport.decorator.SessionedEurekaHttpClient;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.Names;
import com.netflix.eureka.resources.ServerCodecs;

public class Jersey3EurekaServerHttpClientFactory implements EurekaServerHttpClientFactory {

	public static final long RECONNECT_INTERVAL_MINUTES = 30;

	@Override
	public EurekaHttpClient createRemoteRegionClient(EurekaServerConfig serverConfig, EurekaTransportConfig transportConfig, ServerCodecs serverCodecs, ClusterResolver<EurekaEndpoint> clusterResolver) {
		Jersey3RemoteRegionClientFactory jersey3RemoteRegionClientFactory = new Jersey3RemoteRegionClientFactory(serverConfig, serverCodecs, clusterResolver.getRegion());
		TransportClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jersey3RemoteRegionClientFactory);

		SessionedEurekaHttpClient client = new SessionedEurekaHttpClient(
				Names.REMOTE,
				RetryableEurekaHttpClient.createFactory(
						Names.REMOTE,
						transportConfig,
						clusterResolver,
						createFactory(metricsFactory),
						ServerStatusEvaluators.legacyEvaluator()),
				RECONNECT_INTERVAL_MINUTES * 60 * 1000
		);
		return client;
	}

	public static TransportClientFactory createFactory(final TransportClientFactory delegateFactory) {
		final DnsServiceImpl dnsService = new DnsServiceImpl();
		return new TransportClientFactory() {
			@Override
			public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
				return new RedirectingEurekaHttpClient(endpoint.getServiceUrl(), delegateFactory, dnsService);
			}

			@Override
			public void shutdown() {
				delegateFactory.shutdown();
			}
		};
	}
}

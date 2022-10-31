package com.netflix.eureka.transport;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.jersey3.EurekaIdentityHeaderFilter;
import com.netflix.discovery.shared.transport.jersey3.EurekaJersey3Client;
import com.netflix.discovery.shared.transport.jersey3.EurekaJersey3ClientImpl;
import com.netflix.discovery.shared.transport.jersey3.Jersey3ApplicationClient;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.GzipEncodingEnforcingFilter;
import com.netflix.eureka.resources.ServerCodecs;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.MultivaluedHashMap;
import org.glassfish.jersey.message.GZipEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Jersey3RemoteRegionClientFactory implements TransportClientFactory {

	private static final Logger logger = LoggerFactory.getLogger(Jersey3RemoteRegionClientFactory.class);

	private final EurekaServerConfig serverConfig;
	private final ServerCodecs serverCodecs;
	private final String region;

	private volatile EurekaJersey3Client jerseyClient;
	private final Object lock = new Object();

	@Inject
	public Jersey3RemoteRegionClientFactory(EurekaServerConfig serverConfig,
											ServerCodecs serverCodecs,
											String region) {
		this.serverConfig = serverConfig;
		this.serverCodecs = serverCodecs;
		this.region = region;
	}

	@Override
	public EurekaHttpClient newClient(EurekaEndpoint endpoint) {
		return new Jersey3ApplicationClient(getOrCreateJerseyClient(region, endpoint).getClient(), endpoint.getServiceUrl(), new MultivaluedHashMap<>());
	}

	@Override
	public void shutdown() {
		if (jerseyClient != null) {
			jerseyClient.destroyResources();
		}
	}

	private EurekaJersey3Client getOrCreateJerseyClient(String region, EurekaEndpoint endpoint) {
		if (jerseyClient != null) {
			return jerseyClient;
		}

		synchronized (lock) {
			if (jerseyClient == null) {
				EurekaJersey3ClientImpl.EurekaJersey3ClientBuilder clientBuilder = new EurekaJersey3ClientImpl.EurekaJersey3ClientBuilder()
						.withUserAgent("Java-EurekaClient-RemoteRegion")
						.withEncoderWrapper(serverCodecs.getFullJsonCodec())
						.withDecoderWrapper(serverCodecs.getFullJsonCodec())
						.withConnectionTimeout(serverConfig.getRemoteRegionConnectTimeoutMs())
						.withReadTimeout(serverConfig.getRemoteRegionReadTimeoutMs())
						.withMaxConnectionsPerHost(serverConfig.getRemoteRegionTotalConnectionsPerHost())
						.withMaxTotalConnections(serverConfig.getRemoteRegionTotalConnections())
						.withConnectionIdleTimeout(serverConfig.getRemoteRegionConnectionIdleTimeoutSeconds());

				if (endpoint.isSecure()) {
					clientBuilder.withClientName("Discovery-RemoteRegionClient-" + region);
				} else if ("true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
					clientBuilder.withClientName("Discovery-RemoteRegionSystemSecureClient-" + region)
							.withSystemSSLConfiguration();
				} else {
					clientBuilder.withClientName("Discovery-RemoteRegionSecureClient-" + region)
							.withTrustStoreFile(
									serverConfig.getRemoteRegionTrustStore(),
									serverConfig.getRemoteRegionTrustStorePassword()
							);
				}
				jerseyClient = clientBuilder.build();
				Client client = jerseyClient.getClient();

				// Add gzip content encoding support
				boolean enableGZIPContentEncodingFilter = serverConfig.shouldGZipContentFromRemoteRegion();
				if (enableGZIPContentEncodingFilter) {
					client.register((Feature) context -> {
						context.register(new GzipEncodingEnforcingFilter());
						// this filter doesn't add gzip Content-Encoding header
						context.register(new GZipEncoder());
						return true;
					});
				}

				// always enable client identity headers
				String ip = null;
				try {
					ip = InetAddress.getLocalHost().getHostAddress();
				} catch (UnknownHostException e) {
					logger.warn("Cannot find localhost ip", e);
				}
				EurekaServerIdentity identity = new EurekaServerIdentity(ip);
				client.register((Feature) context -> {
					context.register(new EurekaIdentityHeaderFilter(identity));
					return true;
				});
			}
		}

		return jerseyClient;
	}
}

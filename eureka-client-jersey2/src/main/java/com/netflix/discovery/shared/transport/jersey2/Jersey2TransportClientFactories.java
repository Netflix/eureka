package com.netflix.discovery.shared.transport.jersey2;

import java.util.Collection;
import java.util.Optional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientRequestFilter;

import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;

public class Jersey2TransportClientFactories implements TransportClientFactories<ClientRequestFilter> {
    
    private static final Jersey2TransportClientFactories INSTANCE = new Jersey2TransportClientFactories();
    
    public static Jersey2TransportClientFactories getInstance() {
        return INSTANCE;
    }

    @Override
    public TransportClientFactory newTransportClientFactory(final EurekaClientConfig clientConfig,
                                                                   final Collection<ClientRequestFilter> additionalFilters,
                                                                   final InstanceInfo myInstanceInfo) {
        return newTransportClientFactory(clientConfig, additionalFilters, myInstanceInfo, Optional.empty(), Optional.empty());
    }
    
    @Override
    public TransportClientFactory newTransportClientFactory(EurekaClientConfig clientConfig,
            Collection<ClientRequestFilter> additionalFilters, InstanceInfo myInstanceInfo,
            Optional<SSLContext> sslContext, Optional<HostnameVerifier> hostnameVerifier) {
        final TransportClientFactory jerseyFactory = Jersey2ApplicationClientFactory.create(
                clientConfig,
                additionalFilters,
                myInstanceInfo,
                new EurekaClientIdentity(myInstanceInfo.getIPAddr(), "Jersey2DefaultClient"),
                sslContext,
                hostnameVerifier
        );
        final TransportClientFactory metricsFactory = MetricsCollectingEurekaHttpClient.createFactory(jerseyFactory);

        return new TransportClientFactory() {
            @Override
            public EurekaHttpClient newClient(EurekaEndpoint serviceUrl) {
                return metricsFactory.newClient(serviceUrl);
            }

            @Override
            public void shutdown() {
                metricsFactory.shutdown();
                jerseyFactory.shutdown();
            }
        };
    }

    @Override
    public TransportClientFactory newTransportClientFactory(Collection<ClientRequestFilter> additionalFilters,
            EurekaJerseyClient providedJerseyClient) {
        throw new UnsupportedOperationException();
    }

}
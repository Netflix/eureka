package com.netflix.discovery.shared.transport;

import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.transport.decorator.MetricsCollectingEurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;
import com.sun.jersey.api.client.filter.ClientFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

import java.util.Collection;

/**
 * @deprecated This class exist to provide some legacy functionality, that will be removed at some point in time.
 */
@Deprecated
public final class LegacyEurekaHttpClients {

    public static TransportClientFactory newTransportClientFactory(final Collection<ClientFilter> additionalFilters,
                                                                   final EurekaJerseyClient providedJerseyClient) {
        ApacheHttpClient4 apacheHttpClient = providedJerseyClient.getClient();
        if (additionalFilters != null) {
            for (ClientFilter filter : additionalFilters) {
                if (filter != null) {
                    apacheHttpClient.addFilter(filter);
                }
            }
        }

        final TransportClientFactory jerseyFactory = new JerseyEurekaHttpClientFactory(providedJerseyClient, false);
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

}

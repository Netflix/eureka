package com.netflix.discovery.shared.transport.jersey;

import java.util.Collection;
import java.util.Optional;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.TransportClientFactory;

public interface TransportClientFactories<F> {
    
    @Deprecated
    public TransportClientFactory newTransportClientFactory(final Collection<F> additionalFilters,
                                                                   final EurekaJerseyClient providedJerseyClient);

    public TransportClientFactory newTransportClientFactory(final EurekaClientConfig clientConfig,
                                                                   final Collection<F> additionalFilters,
                                                                   final InstanceInfo myInstanceInfo);
    
    public TransportClientFactory newTransportClientFactory(final EurekaClientConfig clientConfig,
            final Collection<F> additionalFilters,
            final InstanceInfo myInstanceInfo,
            final Optional<SSLContext> sslContext,
            final Optional<HostnameVerifier> hostnameVerifier);
}
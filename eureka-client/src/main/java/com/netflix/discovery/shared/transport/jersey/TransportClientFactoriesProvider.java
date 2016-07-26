package com.netflix.discovery.shared.transport.jersey;

import javax.inject.Provider;

public class TransportClientFactoriesProvider implements Provider<TransportClientFactories<?>> {

    private final TransportClientFactories<?> transportClientFactories;

    public TransportClientFactoriesProvider(TransportClientFactories<?> transportClientFactories) {
        if (transportClientFactories == null) {
            // Default to the jersey1 transport client factory
            this.transportClientFactories = new Jersey1TransportClientFactories();
        } else {
            this.transportClientFactories = transportClientFactories;
        }
    }

    @Override
    public TransportClientFactories<?> get() {
        return transportClientFactories;
    }
}

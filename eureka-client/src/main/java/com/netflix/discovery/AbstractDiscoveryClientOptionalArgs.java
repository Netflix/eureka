package com.netflix.discovery;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import jakarta.inject.Provider;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import jakarta.inject.Inject;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.eventbus.spi.EventBus;

/**
 * <T> The type for client supplied filters (supports jersey3)
 */
public abstract class AbstractDiscoveryClientOptionalArgs<T> {
    Provider<HealthCheckCallback> healthCheckCallbackProvider;

    Provider<HealthCheckHandler> healthCheckHandlerProvider;

    PreRegistrationHandler preRegistrationHandler;

    Collection<T> additionalFilters;

    TransportClientFactory transportClientFactory;

    private Set<EurekaEventListener> eventListeners;

    private Optional<SSLContext> sslContext = Optional.empty();

    private Optional<HostnameVerifier> hostnameVerifier = Optional.empty();

    // @Inject
    public void setEventListeners(Set<EurekaEventListener> listeners) {
        if (eventListeners == null) {
            eventListeners = new HashSet<>();
        }
        eventListeners.addAll(listeners);
    }
    
    // @Inject
    public void setEventBus(final EventBus eventBus) {
        if (eventListeners == null) {
            eventListeners = new HashSet<>();
        }
        
        eventListeners.add(new EurekaEventListener() {
            @Override
            public void onEvent(EurekaEvent event) {
                eventBus.publish(event);
            }
        });
    }

    // @Inject
    public void setHealthCheckCallbackProvider(Provider<HealthCheckCallback> healthCheckCallbackProvider) {
        this.healthCheckCallbackProvider = healthCheckCallbackProvider;
    }

    // @Inject
    public void setHealthCheckHandlerProvider(Provider<HealthCheckHandler> healthCheckHandlerProvider) {
        this.healthCheckHandlerProvider = healthCheckHandlerProvider;
    }

    // @Inject
    public void setPreRegistrationHandler(PreRegistrationHandler preRegistrationHandler) {
        this.preRegistrationHandler = preRegistrationHandler;
    }


    // @Inject
    public void setAdditionalFilters(Collection<T> additionalFilters) {
        this.additionalFilters = additionalFilters;
    }

    public TransportClientFactory getTransportClientFactory() {
        return transportClientFactory;
    }

    public void setTransportClientFactory(TransportClientFactory transportClientFactory) {
        this.transportClientFactory = transportClientFactory;
    }

    Set<EurekaEventListener> getEventListeners() {
        return eventListeners == null ? Collections.<EurekaEventListener>emptySet() : eventListeners;
    }

    public Optional<SSLContext> getSSLContext() {
        return sslContext;
    }

    // @Inject
    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = Optional.of(sslContext);
    }
    
    public Optional<HostnameVerifier> getHostnameVerifier() {
        return hostnameVerifier;
    }

    // @Inject
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = Optional.of(hostnameVerifier);
    }
}
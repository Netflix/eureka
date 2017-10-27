package com.netflix.discovery;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import javax.inject.Provider;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import com.google.inject.Inject;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import com.netflix.eventbus.spi.EventBus;

/**
 * <T> The type for client supplied filters (supports jersey1 and jersey2)
 */
public abstract class AbstractDiscoveryClientOptionalArgs<T> {
    Provider<HealthCheckCallback> healthCheckCallbackProvider;

    Provider<HealthCheckHandler> healthCheckHandlerProvider;

    PreRegistrationHandler preRegistrationHandler;

    Collection<T> additionalFilters;

    EurekaJerseyClient eurekaJerseyClient;
    
    TransportClientFactory transportClientFactory;
    
    TransportClientFactories transportClientFactories;

    private Set<EurekaEventListener> eventListeners;

    private Optional<SSLContext> sslContext = Optional.empty();

    private Optional<HostnameVerifier> hostnameVerifier = Optional.empty();

    @Inject(optional = true)
    public void setEventListeners(Set<EurekaEventListener> listeners) {
        if (eventListeners == null) {
            eventListeners = new HashSet<>();
        }
        eventListeners.addAll(listeners);
    }
    
    @Inject(optional = true)
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

    @Inject(optional = true) 
    public void setHealthCheckCallbackProvider(Provider<HealthCheckCallback> healthCheckCallbackProvider) {
        this.healthCheckCallbackProvider = healthCheckCallbackProvider;
    }

    @Inject(optional = true) 
    public void setHealthCheckHandlerProvider(Provider<HealthCheckHandler> healthCheckHandlerProvider) {
        this.healthCheckHandlerProvider = healthCheckHandlerProvider;
    }

    @Inject(optional = true)
    public void setPreRegistrationHandler(PreRegistrationHandler preRegistrationHandler) {
        this.preRegistrationHandler = preRegistrationHandler;
    }


    @Inject(optional = true) 
    public void setAdditionalFilters(Collection<T> additionalFilters) {
        this.additionalFilters = additionalFilters;
    }

    @Inject(optional = true) 
    public void setEurekaJerseyClient(EurekaJerseyClient eurekaJerseyClient) {
        this.eurekaJerseyClient = eurekaJerseyClient;
    }
    
    Set<EurekaEventListener> getEventListeners() {
        return eventListeners == null ? Collections.<EurekaEventListener>emptySet() : eventListeners;
    }
    
    public TransportClientFactories getTransportClientFactories() {
        return transportClientFactories;
    }

    @Inject(optional = true)
    public void setTransportClientFactories(TransportClientFactories transportClientFactories) {
        this.transportClientFactories = transportClientFactories;
    }
    
    public Optional<SSLContext> getSSLContext() {
        return sslContext;
    }

    @Inject(optional = true)
    public void setSSLContext(SSLContext sslContext) {
        this.sslContext = Optional.of(sslContext);
    }
    
    public Optional<HostnameVerifier> getHostnameVerifier() {
        return hostnameVerifier;
    }

    @Inject(optional = true)
    public void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = Optional.of(hostnameVerifier);
    }
}
package com.netflix.eureka2.server.service.bootstrap;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;

import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolver;
import com.netflix.eureka2.server.resolver.EurekaEndpointResolvers;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class PeerBootstrapResolverProvider implements Provider<EurekaEndpointResolver> {

    private final EurekaEndpointResolver resolver;

    @Inject
    public PeerBootstrapResolverProvider(WriteServerConfig config) {
        if (config.getBootstrapServerList() != null) {
            resolver = EurekaEndpointResolvers.readServerResolverFromConfiguration(
                    config.getBootstrapResolverType(),
                    Arrays.asList(config.getBootstrapServerList()),
                    Schedulers.computation()
            );
        } else {
            resolver = EurekaEndpointResolvers.writeServerResolverFromConfiguration(
                    config.getServerResolverType(),
                    Arrays.asList(config.getServerList()),
                    Schedulers.computation()
            );
        }
    }

    @Override
    public EurekaEndpointResolver get() {
        return resolver;
    }
}

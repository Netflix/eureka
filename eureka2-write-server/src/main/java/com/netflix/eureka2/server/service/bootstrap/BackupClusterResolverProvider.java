package com.netflix.eureka2.server.service.bootstrap;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;

import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.resolver.EurekaClusterResolver;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class BackupClusterResolverProvider implements Provider<EurekaClusterResolver> {

    private final EurekaClusterResolver resolver;

    @Inject
    public BackupClusterResolverProvider(WriteServerConfig config) {
        if (config.getBootstrapServerList() != null) {
            resolver = EurekaClusterResolvers.readClusterResolverFromConfiguration(
                    config.getBootstrapResolverType(),
                    Arrays.asList(config.getBootstrapServerList()),
                    Schedulers.computation()
            );
        } else {
            resolver = EurekaClusterResolvers.writeClusterResolverFromConfiguration(
                    config.getServerResolverType(),
                    Arrays.asList(config.getServerList()),
                    Schedulers.computation()
            );
        }
    }

    @Override
    public EurekaClusterResolver get() {
        return resolver;
    }
}

package com.netflix.eureka;

import com.google.inject.Scopes;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka.guice.EurekaServerModule;
import com.netflix.eureka.resources.ASGResource;
import com.netflix.eureka.resources.ApplicationsResource;
import com.netflix.eureka.resources.InstancesResource;
import com.netflix.eureka.resources.PeerReplicationResource;
import com.netflix.eureka.resources.SecureVIPResource;
import com.netflix.eureka.resources.ServerInfoResource;
import com.netflix.eureka.resources.StatusResource;
import com.netflix.eureka.resources.VIPResource;
import com.netflix.governator.GovernatorFeatures;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.ProvisionDebugModule;
import com.netflix.governator.guice.annotations.Bootstrap;
import com.netflix.governator.guice.jetty.JettyModule;
import com.netflix.karyon.DefaultKaryonConfiguration;
import com.netflix.karyon.Karyon;
import com.netflix.karyon.archaius.ArchaiusKaryonConfiguration;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David Liu
 */
public class EurekaInjectorCreator {
    private static final Logger logger = LoggerFactory.getLogger(EurekaInjectorCreator.class);

    private static final String CONFIG_NAME = "eureka-server";

    public static LifecycleInjector createInjector(boolean embedded) {
        try {
            return Karyon.createInjector(
                    createInjectorBuilder(embedded).build()
            );
        } catch (Exception e) {
            logger.error("Failed to create the injector", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static DefaultKaryonConfiguration.Builder<?> createInjectorBuilder(boolean embedded) {
        return  ArchaiusKaryonConfiguration.builder()
                .withConfigName(CONFIG_NAME)
                .disable(GovernatorFeatures.SHUTDOWN_ON_ERROR)
                .addModules(
                        new EurekaModule(),  // client
                        new EurekaServerModule(),  // server
                        new ArchaiusModule(),
                        new ProvisionDebugModule(),
                        new JerseyServletModule() {
                            @Override
                            protected void configureServlets() {
                                filter("/*").through(StatusFilter.class);
                                filter("/*").through(ServerRequestAuthFilter.class);
                                filter("/v2/apps", "/v2/apps/*").through(GzipEncodingEnforcingFilter.class);
                                //filter("/*").through(RateLimitingFilter.class);

                                // REST
                                serve("/*").with(GuiceContainer.class);
                                bind(GuiceContainer.class).asEagerSingleton();

                                bind(ApplicationsResource.class).in(Scopes.SINGLETON);
                                bind(ASGResource.class).in(Scopes.SINGLETON);
                                bind(InstancesResource.class).in(Scopes.SINGLETON);
                                bind(PeerReplicationResource.class).in(Scopes.SINGLETON);
                                bind(SecureVIPResource.class).in(Scopes.SINGLETON);
                                bind(ServerInfoResource.class).in(Scopes.SINGLETON);
                                bind(StatusResource.class).in(Scopes.SINGLETON);
                                bind(VIPResource.class).in(Scopes.SINGLETON);
                            }
                        },
                        embedded ? new JettyModule() : new Bootstrap.NullModule()
                );
    }
}

package com.netflix.eureka;

import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka.guice.EurekaServerModule;
import com.netflix.eureka.guice.LocalEurekaServerModule;
import com.netflix.governator.GovernatorFeatures;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.ProvisionDebugModule;
import com.netflix.governator.guice.annotations.Bootstrap;
import com.netflix.governator.guice.jetty.JettyModule;
import com.netflix.karyon.DefaultKaryonConfiguration;
import com.netflix.karyon.Karyon;
import com.netflix.karyon.archaius.ArchaiusKaryonConfiguration;
import com.sun.jersey.api.core.PackagesResourceConfig;
import com.sun.jersey.guice.JerseyServletModule;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

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
                        new LocalEurekaServerModule(),  // server
                        new ArchaiusModule(),
                        new ProvisionDebugModule(),
                        new JerseyServletModule() {
                            @Override
                            protected void configureServlets() {
                                filter("/*").through(StatusFilter.class);
                                filter("/*").through(ServerRequestAuthFilter.class);
                                filter("/v2/apps", "/v2/apps/*").through(GzipEncodingEnforcingFilter.class);
                                //filter("/*").through(RateLimitingFilter.class);  // enable if needed

                                // REST
                                Map<String, String> params = new HashMap<String, String>();
                                params.put(PackagesResourceConfig.PROPERTY_PACKAGES, "com.sun.jersey");
                                params.put(PackagesResourceConfig.PROPERTY_PACKAGES, "com.netflix");
                                params.put("com.sun.jersey.config.property.WebPageContentRegex", "/(flex|images|js|css|jsp)/.*");
                                params.put("com.sun.jersey.spi.container.ContainerRequestFilters", "com.sun.jersey.api.container.filter.GZIPContentEncodingFilter");
                                params.put("com.sun.jersey.spi.container.ContainerResponseFilters", "com.sun.jersey.api.container.filter.GZIPContentEncodingFilter");
                                filter("/*").through(GuiceContainer.class, params);
                                bind(GuiceContainer.class).asEagerSingleton();
                            }
                        },
                        embedded ? new JettyModule() : new Bootstrap.NullModule()
                );
    }
}

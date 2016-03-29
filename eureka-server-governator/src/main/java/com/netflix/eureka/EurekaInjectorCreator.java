package com.netflix.eureka;

import com.netflix.discovery.guice.EurekaModule;
import com.netflix.eureka.guice.Ec2EurekaServerModule;
import com.netflix.eureka.guice.LocalDevEurekaServerModule;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.ProvisionDebugModule;
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

    public static LifecycleInjector createInjector() {
        try {
            return InjectorBuilder
                    .fromModules(
                            new EurekaModule(),
                            new Ec2EurekaServerModule(),
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
                            }
                    )
                    .createInjector();
        } catch (Exception e) {
            logger.error("Failed to create the injector", e);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}

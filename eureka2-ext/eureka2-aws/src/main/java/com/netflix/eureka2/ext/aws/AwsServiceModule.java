package com.netflix.eureka2.ext.aws;

import javax.inject.Singleton;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.server.service.overrides.OverridesService;
import com.netflix.eureka2.server.spi.ExtAbstractModule;

/**
 * @author Tomasz Bak
 */
public class AwsServiceModule extends ExtAbstractModule {

    private static final String AWS_CONFIG_PREFIX = "eureka.ext.aws";

    @Override
    protected void configure() {
        bind(AmazonAutoScaling.class).toProvider(AmazonAutoScalingProvider.class);
        bind(AmazonS3Client.class).toProvider(AmazonS3ClientProvider.class);

        bind(AsgStatusOverridesView.class).in(Scopes.SINGLETON);
        bind(S3StatusOverridesRegistry.class).in(Scopes.SINGLETON);

        MapBinder<Integer, OverridesService> mapbinder = MapBinder.newMapBinder(binder(), Integer.class, OverridesService.class);
        // for ordering
        mapbinder.addBinding(0).to(AsgStatusOverridesService.class);
        mapbinder.addBinding(1).to(S3StatusOverridesService.class);
    }

    @Provides
    @Singleton
    public AwsConfiguration getAwsConfiguration(ConfigProxyFactory factory) {
        return factory.newProxy(AwsConfiguration.class, AWS_CONFIG_PREFIX);
    }
}

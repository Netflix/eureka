package com.netflix.eureka2.ext.aws;

import javax.inject.Singleton;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.inject.Provides;
import com.google.inject.multibindings.MapBinder;
import com.netflix.archaius.Config;
import com.netflix.archaius.PropertyFactory;
import com.netflix.archaius.ProxyFactory;
import com.netflix.archaius.property.PrefixedObservablePropertyFactory;
import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesSource;
import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesView;
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

        bind(InstanceStatusOverridesView.class).to(AsgStatusOverridesView.class);
        bind(InstanceStatusOverridesView.class).to(S3StatusOverridesRegistry.class);
        bind(InstanceStatusOverridesSource.class).to(S3StatusOverridesRegistry.class);

        MapBinder<Integer, OverridesService> mapbinder = MapBinder.newMapBinder(binder(), Integer.class, OverridesService.class);
        // for ordering
        mapbinder.addBinding(0).to(AsgStatusOverridesService.class);
        mapbinder.addBinding(1).to(S3StatusOverridesService.class);
    }

    @Provides
    @Singleton
    public AwsConfiguration getAwsConfiguration(Config archaiusConfig, PropertyFactory propertyFactory) {
        PrefixedObservablePropertyFactory prefixedPropertyFactory = new PrefixedObservablePropertyFactory(AWS_CONFIG_PREFIX, propertyFactory);
        ProxyFactory proxyFactory = new ProxyFactory(archaiusConfig.getDecoder());
        return proxyFactory.newProxy(AwsConfiguration.class, prefixedPropertyFactory);
    }
}

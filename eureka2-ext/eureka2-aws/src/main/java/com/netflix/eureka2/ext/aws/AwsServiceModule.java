package com.netflix.eureka2.ext.aws;

import javax.inject.Singleton;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.netflix.archaius.Config;
import com.netflix.archaius.PropertyFactory;
import com.netflix.archaius.ProxyFactory;
import com.netflix.archaius.property.PrefixedObservablePropertyFactory;
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

        Multibinder<OverridesService> multibinder = Multibinder.newSetBinder(binder(), OverridesService.class);
        multibinder.addBinding().to(AsgOverrideService.class);
    }

    @Provides
    @Singleton
    public AwsConfiguration getAwsConfiguration(Config archaiusConfig, PropertyFactory propertyFactory) {
        PrefixedObservablePropertyFactory prefixedPropertyFactory = new PrefixedObservablePropertyFactory(AWS_CONFIG_PREFIX, propertyFactory);
        ProxyFactory proxyFactory = new ProxyFactory(archaiusConfig.getDecoder());
        return proxyFactory.newProxy(AwsConfiguration.class, prefixedPropertyFactory);
    }
}

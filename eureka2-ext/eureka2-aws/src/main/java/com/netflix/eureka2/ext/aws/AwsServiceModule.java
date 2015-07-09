package com.netflix.eureka2.ext.aws;

import javax.inject.Singleton;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.s3.AmazonS3Client;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.netflix.archaius.ConfigProxyFactory;
import com.netflix.eureka2.server.service.overrides.CompositeOverridesService;
import com.netflix.eureka2.server.service.overrides.OverridesService;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.governator.auto.annotations.ConditionalOnProfile;

/**
 * @author Tomasz Bak
 */
@ConditionalOnProfile(ExtAbstractModule.WRITE_PROFILE)
public class AwsServiceModule extends ExtAbstractModule {

    private static final String AWS_CONFIG_PREFIX = "eureka2.ext.aws";

    @Override
    protected void configure() {
        bind(AmazonAutoScaling.class).toProvider(AmazonAutoScalingProvider.class);
        bind(AmazonS3Client.class).toProvider(AmazonS3ClientProvider.class);

        bind(AsgStatusOverridesView.class).in(Scopes.SINGLETON);
        bind(S3StatusOverridesRegistry.class).in(Scopes.SINGLETON);

        bind(OverridesService.class).to(CompositeOverridesService.class).in(Scopes.SINGLETON);

        Multibinder<OverridesService> multibinder = Multibinder.newSetBinder(binder(), OverridesService.class);
        multibinder.addBinding().to(AsgStatusOverridesService.class);
        multibinder.addBinding().to(S3StatusOverridesService.class);
    }

    @Provides
    @Singleton
    public AwsConfiguration getAwsConfiguration(ConfigProxyFactory factory) {
        return factory.newProxy(AwsConfiguration.class, AWS_CONFIG_PREFIX);
    }
}

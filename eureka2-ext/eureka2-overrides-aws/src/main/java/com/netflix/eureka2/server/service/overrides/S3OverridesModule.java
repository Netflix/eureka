package com.netflix.eureka2.server.service.overrides;

import com.google.inject.AbstractModule;

/**
 * Override the default {@link com.netflix.eureka2.server.service.overrides.OverridesModule} with this module
 * to use the S3 backed OUT_OF_SERVICE overrides source.
 *
 * @author David Liu
 */
public class S3OverridesModule extends AbstractModule {
    @Override
    protected void configure() {
        requireBinding(OverridesRegistry.class);

        bind(LoadingOverridesRegistry.ExternalOverridesSource.class).to(S3OutOfServiceOverridesSource.class);
    }
}

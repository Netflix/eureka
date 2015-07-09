package com.netflix.eureka2.eureka1.rest;

import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.governator.auto.annotations.ConditionalOnProfile;

/**
 * @author Tomasz Bak
 */
@ConditionalOnProfile(ExtAbstractModule.WRITE_PROFILE)
public class Eureka1RestApiWriteModule extends ExtAbstractModule {

    @Override
    protected void configure() {
        bind(Eureka1RedirectRequestHandler.class).asEagerSingleton();
    }
}

package com.netflix.eureka.guice;

import com.netflix.karyon.conditional.ConditionalOnLocalDev;

/**
 * @author David Liu
 */
@ConditionalOnLocalDev
public class LocalDevEurekaClientModule extends com.netflix.discovery.guice.LocalDevEurekaClientModule {
    @Override
    public boolean equals(Object obj) {
        return LocalDevEurekaClientModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return LocalDevEurekaClientModule.class.hashCode();
    }
}

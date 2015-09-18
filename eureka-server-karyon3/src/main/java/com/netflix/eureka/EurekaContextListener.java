package com.netflix.eureka;

import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.guice.servlet.GovernatorServletContextListener;

/**
 * @author David Liu
 */
public class EurekaContextListener extends GovernatorServletContextListener {

    @Override
    protected LifecycleInjector createInjector() {
        return EurekaInjectorCreator.createInjector(false);
    }
}

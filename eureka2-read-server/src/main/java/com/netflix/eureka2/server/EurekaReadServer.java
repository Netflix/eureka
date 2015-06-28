package com.netflix.eureka2.server;

import javax.inject.Inject;

import com.google.inject.Injector;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServer extends AbstractEurekaServer {

    @Inject
    public EurekaReadServer(Injector injector) {
        super(injector);
    }
}

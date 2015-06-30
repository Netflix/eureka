package com.netflix.eureka2.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Injector;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaBridgeServer extends EurekaWriteServer {
    @Inject
    public EurekaBridgeServer(Injector injector) {
        super(injector);
    }
}

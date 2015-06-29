package com.netflix.eureka2.server;

import com.google.inject.Injector;

/**
 * @author Tomasz Bak
 */
public class EurekaBridgeServer extends EurekaWriteServer {
    public EurekaBridgeServer(Injector injector) {
        super(injector);
    }
}

package com.netflix.eureka2;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Injector;
import com.netflix.eureka2.server.AbstractEurekaServer;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaDashboardServer extends AbstractEurekaServer {
    @Inject
    public EurekaDashboardServer(Injector injector) {
        super(injector);
    }

    public int getDashboardPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(DashboardHttpServer.class).serverPort();
    }
}
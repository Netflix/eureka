package com.netflix.eureka2.testkit.embedded.server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationHandler;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EmbeddedTcpRegistrationServer extends TcpRegistrationServer {

    private final NetworkRouter networkRouter;
    private int proxyPort;

    @Inject
    public EmbeddedTcpRegistrationServer(EurekaServerTransportConfig config,
                                         @Named(Names.REGISTRATION) MetricEventsListenerFactory servoEventsListenerFactory,
                                         TcpRegistrationHandler tcpRegistrationHandler,
                                         NetworkRouter networkRouter) {
        super(config, servoEventsListenerFactory, tcpRegistrationHandler);
        this.networkRouter = networkRouter;
    }

    @PostConstruct
    @Override
    public void start() {
        // FIXME For some reason start method is called twice and we must guard against it here
        if (server == null) {
            super.start();
            proxyPort = networkRouter.bridgeTo(super.serverPort());
        }
    }

    @PreDestroy
    @Override
    public void stop() {
        networkRouter.removeBridgeTo(super.serverPort());
        super.stop();
    }

    @Override
    public int serverPort() {
        return proxyPort;
    }
}

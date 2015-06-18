package com.netflix.eureka2.testkit.embedded.server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryHandler;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EmbeddedTcpDiscoveryServer extends TcpDiscoveryServer {

    private final NetworkRouter networkRouter;
    private int proxyPort;

    @Inject
    public EmbeddedTcpDiscoveryServer(EurekaServerConfig config,
                                      @Named("discovery") MetricEventsListenerFactory servoEventsListenerFactory,
                                      Provider<TcpDiscoveryHandler> tcpDiscoveryHandler,
                                      NetworkRouter networkRouter) {
        super(config, servoEventsListenerFactory, tcpDiscoveryHandler);
        this.networkRouter = networkRouter;
    }

    @PostConstruct
    @Override
    public void start() {
        super.start();
        proxyPort = networkRouter.bridgeTo(super.serverPort());
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

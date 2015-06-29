package com.netflix.eureka2.testkit.embedded.server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationHandler;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EmbeddedTcpReplicationServer extends TcpReplicationServer {
    private final NetworkRouter networkRouter;
    private int proxyPort;

    @Inject
    public EmbeddedTcpReplicationServer(WriteServerConfig config,
                                        TcpReplicationHandler tcpReplicationHandler,
                                        @Named(Names.REPLICATION) MetricEventsListenerFactory servoEventsListenerFactory,
                                        NetworkRouter networkRouter) {
        super(config, tcpReplicationHandler, servoEventsListenerFactory);
        this.networkRouter = networkRouter;
    }


    @PostConstruct
    @Override
    public void start() {
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

package com.netflix.eureka2.server.http;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.config.EurekaCommonConfig;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.server.RxServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is prototype implementation of Eureka HTTP server, with many shortcuts to go around problems.
 *
 * @author Tomasz Bak
 */
@Singleton
public class EurekaHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(EurekaHttpServer.class);

    private final EurekaCommonConfig config;
    private final HealthConnectionHandler healthConnectionHandler;

    private RxServer<WebSocketFrame, WebSocketFrame> server;

    @Inject
    public EurekaHttpServer(EurekaCommonConfig config, HealthConnectionHandler healthConnectionHandler) {
        this.config = config;
        this.healthConnectionHandler = healthConnectionHandler;
    }

    @PostConstruct
    public void start() {
        server = RxNetty.newWebSocketServerBuilder(config.getHttpPort(), healthConnectionHandler).build().start();
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            try {
                server.shutdown();
            } catch (InterruptedException e) {
                logger.info("EurekaHttpServer shutdown interrupted", e);
            }
            server = null;
        }
    }

    public int serverPort() {
        return server.getServerPort();
    }
}

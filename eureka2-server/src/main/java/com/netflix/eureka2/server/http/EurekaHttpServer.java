package com.netflix.eureka2.server.http;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.http.proxy.ForwardingRule;
import com.netflix.eureka2.server.http.proxy.RxHttpReverseProxy;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.http.websocket.WebSocketServer;
import io.reactivex.netty.server.RxServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eureka HTTP server endpoint. As RxNetty as of now does not support HTTP/WebSocket protocols
 * on the same server port, multiple backend are created with a fronted proxy implemented by
 * {@link RxHttpReverseProxy} class.
 *
 * @author Tomasz Bak
 */
@Singleton
public class EurekaHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(EurekaHttpServer.class);

    private final List<RxServer<?, ?>> backendServers = new CopyOnWriteArrayList<>();
    private final RxHttpReverseProxy proxy;

    @Inject
    public EurekaHttpServer(EurekaServerTransportConfig config) {
        this.proxy = new RxHttpReverseProxy(config.getHttpPort());
    }

    @PostConstruct
    public void start() {
        proxy.start();
        logger.info("Started HTTP server on port {}", proxy.getServerPort());
    }

    @PreDestroy
    public void stop() {
        proxy.shutdown();
        for (RxServer<?, ?> server : backendServers) {
            try {
                server.shutdown();
            } catch (InterruptedException e) {
                // IGNORE
            }
        }
    }

    public int serverPort() {
        return proxy.getServerPort();
    }

    public <T extends WebSocketFrame> void connectWebSocketEndpoint(String pathPrefix, ConnectionHandler<T, T> handler) {
        WebSocketServer<T, T> backend = RxNetty.newWebSocketServerBuilder(0, handler).build();
        backend.start();
        backendServers.add(backend);
        proxy.register(ForwardingRule.pathPrefix(backend.getServerPort(), pathPrefix));
        logger.info("Started backend WebSocket server on port {} or {}", backend.getServerPort(), handler.getClass().getSimpleName());
    }

    public <I, O> void connectHttpEndpoint(String pathPrefix, RequestHandler<I, O> handler) {
        HttpServer<I, O> backend = RxNetty.newHttpServerBuilder(0, handler).build();
        backend.start();
        backendServers.add(backend);
        proxy.register(ForwardingRule.pathPrefix(backend.getServerPort(), pathPrefix));
        logger.info("Started backend HTTP server on port {} or {}", backend.getServerPort(), handler.getClass().getSimpleName());
    }
}

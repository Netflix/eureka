package com.netflix.eureka2.server.http;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import com.netflix.eureka2.server.http.proxy.ForwardingRule;
import com.netflix.eureka2.server.http.proxy.HttpRequestDispatcher;
import com.netflix.eureka2.server.http.proxy.RxHttpReverseProxy;
import io.netty.buffer.ByteBuf;
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

    // Single HTTP backend server is shared by all handlers.
    private volatile HttpServer<?, ?> httpBackend;
    private final HttpRequestDispatcher httpRequestDispatcher = new HttpRequestDispatcher();

    @Inject
    public EurekaHttpServer(EurekaServerTransportConfig config) {
        this.proxy = new RxHttpReverseProxy(config.getHttpPort());
        // Must start this server early, as registrations happen before start is called
        this.httpBackend = RxNetty.newHttpServerBuilder(0, httpRequestDispatcher).build();
        this.httpBackend.start();
    }

    @PostConstruct
    public void start() {
        proxy.start();
        logger.info("Started HTTP server on port {}", proxy.getServerPort());
    }

    @PreDestroy
    public void stop() {
        proxy.shutdown();
        try {
            httpBackend.shutdown();
        } catch (InterruptedException ignored) {
        }
        for (RxServer<?, ?> server : backendServers) {
            try {
                server.shutdown();
            } catch (InterruptedException ignored) {
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

    public void connectHttpEndpoint(String pathPrefix, RequestHandler<ByteBuf, ByteBuf> handler) {
        httpRequestDispatcher.addHandler(pathPrefix, handler);
        proxy.register(ForwardingRule.pathPrefix(httpBackend.getServerPort(), pathPrefix));
        logger.info("Registering handler {} at path {} in the HTTP server", handler.getClass().getSimpleName(), pathPrefix);
    }
}

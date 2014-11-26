package com.netflix.eureka2;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;


@Singleton
public class WebSocketServer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketServer.class);

    private final EurekaRegistryHandler eurekaRegistryHandler;
    private final EurekaServerStatusHandler eurekaServerStatusHandler;
    private final EurekaDashboardConfig config;

    private RxServer<WebSocketFrame, WebSocketFrame> server;

    @Inject
    public WebSocketServer(EurekaDashboardConfig config,
                           EurekaRegistryHandler eurekaWebSocketHandler,
                           EurekaServerStatusHandler eurekaServerStatusHandler) {
        this.config = config;
        this.eurekaServerStatusHandler = eurekaServerStatusHandler;
        this.eurekaRegistryHandler = eurekaWebSocketHandler;
    }

    @PostConstruct
    public void start() {
        server = RxNetty.newWebSocketServerBuilder(config.getWebSocketPort(), new ConnectionHandler<WebSocketFrame, WebSocketFrame>() {
            @Override
            public Observable<Void> handle(final ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
                return connection.getInput().flatMap(new Func1<WebSocketFrame, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(WebSocketFrame wsFrame) {
                        TextWebSocketFrame textFrame = (TextWebSocketFrame) wsFrame;
                        System.out.println("Got message: " + textFrame.text());
                        final String cmd = textFrame.text();
                        if (cmd.equals("get status")) {
                            return streamEurekaStatus(connection);
                        } else {
                            // registry
                            return streamEurekaRegistryData(connection);
                        }
                    }
                });
            }
        }).build().start();

        logger.info("Starting WebSocket server on port {}...", server.getServerPort());
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (server != null) {
            server.shutdown();
        }
    }

    private Observable<Void> streamEurekaRegistryData(ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
        return eurekaRegistryHandler.buildWebSocketResponse(connection);
    }

    private Observable<Void> streamEurekaStatus(ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
        return eurekaServerStatusHandler.buildWebSocketResponse(connection);
    }
}

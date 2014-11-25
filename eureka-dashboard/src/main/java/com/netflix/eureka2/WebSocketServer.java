package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.functions.Func1;


@Singleton
public class WebSocketServer {

    static final int DEFAULT_PORT = 9000;
    private final int port;
    private final EurekaRegistryHandler eurekaRegistryHandler;
    private EurekaServerStatusHandler eurekaServerStatusHandler;

    @Inject
    public WebSocketServer(EurekaRegistryHandler eurekaWebSocketHandler, EurekaServerStatusHandler eurekaServerStatusHandler) {
        this.port = DEFAULT_PORT;
        this.eurekaServerStatusHandler = eurekaServerStatusHandler;
        this.eurekaRegistryHandler = eurekaWebSocketHandler;
    }

    public RxServer<WebSocketFrame, WebSocketFrame> createServer() {
        RxServer<WebSocketFrame, WebSocketFrame> server = RxNetty.newWebSocketServerBuilder(port, new ConnectionHandler<WebSocketFrame, WebSocketFrame>() {
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
        }).build();

        System.out.println("WebSocket server started...");
        return server;
    }

    private Observable<Void> streamEurekaRegistryData(ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
        return eurekaRegistryHandler.buildWebSocketResponse(connection);
    }

    private Observable<Void> streamEurekaStatus(ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
        return eurekaServerStatusHandler.buildWebSocketResponse(connection);
    }
}

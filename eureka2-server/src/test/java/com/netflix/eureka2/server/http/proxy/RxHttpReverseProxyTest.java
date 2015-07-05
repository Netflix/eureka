package com.netflix.eureka2.server.http.proxy;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.metrics.MetricEventsListener;
import io.reactivex.netty.protocol.http.websocket.WebSocketClient;
import io.reactivex.netty.protocol.http.websocket.WebSocketServer;
import io.reactivex.netty.server.ServerMetricsEvent;
import io.reactivex.netty.server.ServerMetricsEvent.EventType;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public class RxHttpReverseProxyTest {

    private RxHttpReverseProxy reverseProxy;

    private WebSocketServer<TextWebSocketFrame, TextWebSocketFrame> webSocketBackend;

    private WebSocketClient<TextWebSocketFrame, TextWebSocketFrame> webSocketClient;

    @Before
    public void startUp() {
        reverseProxy = new RxHttpReverseProxy(0);
        reverseProxy.start();

        TestableWebSocketHandler webSocketHandler = new TestableWebSocketHandler();

        webSocketBackend = RxNetty.newWebSocketServerBuilder(0, webSocketHandler).build();
        webSocketBackend.start();
        reverseProxy.register(ForwardingRule.any(webSocketBackend.getServerPort()));

        webSocketClient = RxNetty.<TextWebSocketFrame, TextWebSocketFrame>
                newWebSocketClientBuilder("localhost", reverseProxy.getServerPort()).build();
    }

    @Test
    public void testWebSocketDataForwarding() throws Exception {
        String reply = webSocketClient
                .connect()
                .flatMap(new Func1<ObservableConnection<TextWebSocketFrame, TextWebSocketFrame>, Observable<String>>() {
                    @Override
                    public Observable<String> call(ObservableConnection<TextWebSocketFrame, TextWebSocketFrame> connection) {
                        connection.writeAndFlush(new TextWebSocketFrame("Hello"));
                        return connection.getInput().map(new Func1<TextWebSocketFrame, String>() {
                            @Override
                            public String call(TextWebSocketFrame webSocketFrame) {
                                return webSocketFrame.text();
                            }
                        });
                    }
                }).take(1).timeout(3000, TimeUnit.SECONDS).toBlocking().first();
        assertThat(reply, is(equalTo("Hello")));
    }

    @Test
    public void testInternalConnectionCleanupOnExternalDisconnect() throws Exception {
        final BlockingQueue<ServerMetricsEvent<?>> backendEvents = new LinkedBlockingQueue<>();
        webSocketBackend.getEventsSubject().subscribe(new MetricEventsListener<ServerMetricsEvent<?>>() {
            @Override
            public void onEvent(ServerMetricsEvent<?> event, long duration, TimeUnit timeUnit, Throwable throwable, Object value) {
                backendEvents.add(event);
            }

            @Override
            public void onCompleted() {
            }

            @Override
            public void onSubscribe() {
            }
        });
        webSocketClient
                .connect()
                .flatMap(new Func1<ObservableConnection<TextWebSocketFrame, TextWebSocketFrame>, Observable<String>>() {
                    @Override
                    public Observable<String> call(ObservableConnection<TextWebSocketFrame, TextWebSocketFrame> connection) {
                        connection.close();
                        return Observable.empty();
                    }
                }).timeout(30, TimeUnit.SECONDS).toBlocking().firstOrDefault(null);

        ServerMetricsEvent<?> event;
        do {
            event = backendEvents.poll(5, TimeUnit.SECONDS);
            if (event == null) {
                fail("Backend server close event not received in time");
            }
        } while (event.getType() != EventType.ConnectionCloseSuccess);
    }

    static class TestableWebSocketHandler implements ConnectionHandler<TextWebSocketFrame, TextWebSocketFrame> {

        @Override
        public Observable<Void> handle(final ObservableConnection<TextWebSocketFrame, TextWebSocketFrame> newConnection) {
            return newConnection.getInput().take(1).flatMap(new Func1<TextWebSocketFrame, Observable<Void>>() {
                @Override
                public Observable<Void> call(TextWebSocketFrame textWebSocketFrame) {
                    return newConnection.writeAndFlush(textWebSocketFrame);
                }
            });
        }
    }
}
package com.netflix.eureka2.testkit.netrouter.internal;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public class RouterPortTest {

    private RouterPort routerPort;

    private RxServer<ByteBuf, ByteBuf> server;

    @Before
    public void setUp() throws Exception {
        server = RxNetty.newTcpServerBuilder(0, new ConnectionHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(final ObservableConnection<ByteBuf, ByteBuf> newConnection) {
                return newConnection.getInput().flatMap(new Func1<ByteBuf, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ByteBuf byteBuf) {
                        return newConnection.writeAndFlush(byteBuf.retain());
                    }
                });
            }
        }).build().start();
        routerPort = new RouterPort(server.getServerPort());
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    public void testDataAreForwardedToTargetPort() throws Exception {
        assertThat(sendHello(), is(equalTo("REPLY: HELLO!")));
    }

    @Test
    public void testLinkCanBeDisconnectedAndConnectedAgain() throws Exception {
        // First bring the link down and verify that connectivity is blocked
        routerPort.getLink().disconnect();
        try {
            sendHello();
            fail("Connection failure expected");
        } catch (Exception e) {
            // Good, connection failed as expected
        }

        // Now bring the link up, and verify that connectivity works again
        routerPort.getLink().connect();
        assertThat(sendHello(), is(equalTo("REPLY: HELLO!")));
    }

    private String sendHello() {
        return RxNetty.<ByteBuf, ByteBuf>newTcpClientBuilder("localhost", routerPort.getLocalPort()).build()
                .connect()
                .flatMap(new Func1<ObservableConnection<ByteBuf, ByteBuf>, Observable<String>>() {
                    @Override
                    public Observable<String> call(final ObservableConnection<ByteBuf, ByteBuf> connection) {
                        connection.writeStringAndFlush("HELLO!");
                        return connection.getInput().map(new Func1<ByteBuf, String>() {
                            @Override
                            public String call(ByteBuf byteBuf) {
                                connection.close();
                                return "REPLY: " + byteBuf.toString(Charset.defaultCharset());
                            }
                        });
                    }
                }).reduce(new StringBuilder(), new Func2<StringBuilder, String, StringBuilder>() {
                    @Override
                    public StringBuilder call(StringBuilder accumulator, String delta) {
                        return accumulator.append(delta);
                    }
                })
                .timeout(10, TimeUnit.SECONDS)
                .toBlocking()
                .first().toString();
    }
}
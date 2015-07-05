package com.netflix.eureka2.server.http.proxy;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;


/**
 * RxNetty server does not allow as of now running different HTTP family protocols on the same
 * port. {@link RxHttpReverseProxy} provides a workaround by running separate RxNetty servers
 * per protocol type, which are all hidden behind a proxy.
 *
 * @author Tomasz Bak
 */
public class RxHttpReverseProxy {

    private final ForwardingHandler forwardingHandler;
    private final RxServer<ByteBuf, ByteBuf> server;

    public RxHttpReverseProxy(int proxyPort) {
        forwardingHandler = new ForwardingHandler();
        server = RxNetty.createTcpServer(proxyPort, forwardingHandler);
    }

    public void register(ForwardingRule rule) {
        forwardingHandler.register(rule);
    }

    public int getServerPort() {
        return server.getServerPort();
    }

    public void start() {
        server.start();
    }

    public void shutdown() {
        try {
            server.shutdown();
        } catch (InterruptedException e) {
            // IGONRE
        }
    }

    static class ForwardingHandler implements ConnectionHandler<ByteBuf, ByteBuf> {
        private static final Logger logger = LoggerFactory.getLogger(ForwardingHandler.class);

        private final List<ForwardingRule> forwardingRules = new CopyOnWriteArrayList<>();

        public void register(ForwardingRule rule) {
            forwardingRules.add(rule);
        }

        @Override
        public Observable<Void> handle(final ObservableConnection<ByteBuf, ByteBuf> proxyConnection) {
            final AtomicReference<ObservableConnection<ByteBuf, ByteBuf>> targetConnectionRef = new AtomicReference<>();

            return proxyConnection.getInput().flatMap(new Func1<ByteBuf, Observable<Void>>() {

                private final ConcurrentLinkedQueue<ByteBuf> requestBuf = new ConcurrentLinkedQueue<>();
                private final StringBuffer sb = new StringBuffer();
                private boolean targetFound;

                @Override
                public Observable<Void> call(ByteBuf byteBuf) {
                    byteBuf.retain(1);

                    if (targetConnectionRef.get() != null) {
                        return targetConnectionRef.get().writeAndFlush(byteBuf);
                    }

                    requestBuf.add(byteBuf);
                    if (targetFound) {
                        // This condition is needed as RxNetty may deliver another ByteBuf before RxClient connection
                        // observable below completes. Why?
                        return Observable.empty();
                    }
                    sb.append(byteBuf.toString(Charset.defaultCharset()));

                    String path = extractPath(sb);
                    if (path == null) {
                        return Observable.empty();
                    }

                    int targetPort = findTarget(path);
                    if (targetPort == -1) {
                        return replyWithNotFoundError(proxyConnection);
                    }
                    targetFound = true;
                    return RxNetty.createTcpClient("localhost", targetPort).connect().flatMap(
                            new Func1<ObservableConnection<ByteBuf, ByteBuf>, Observable<Void>>() {
                                @Override
                                public Observable<Void> call(final ObservableConnection<ByteBuf, ByteBuf> newConnection) {
                                    targetConnectionRef.set(newConnection);
                                    newConnection.getInput().flatMap(new Func1<ByteBuf, Observable<Void>>() {
                                        @Override
                                        public Observable<Void> call(ByteBuf byteBuf) {
                                            byteBuf.retain();
                                            return proxyConnection.writeAndFlush(byteBuf);
                                        }
                                    }).subscribe(new Subscriber<Void>() {
                                        @Override
                                        public void onCompleted() {
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            targetConnectionRef.get().close();
                                            proxyConnection.close();
                                        }

                                        @Override
                                        public void onNext(Void aVoid) {
                                        }
                                    });
                                    for (ByteBuf byteBuf : requestBuf) {
                                        newConnection.write(byteBuf);
                                    }
                                    return newConnection.flush();
                                }
                            });
                }
            }).doOnTerminate(new Action0() {
                @Override
                public void call() {
                    if (targetConnectionRef.get() != null) {
                        targetConnectionRef.get().close();
                    }
                }
            });
        }

        private static Observable<Void> replyWithNotFoundError(ObservableConnection<ByteBuf, ByteBuf> connection) {
            return connection.writeStringAndFlush("HTTP/1.1 404 Not Found\n" +
                            "Content-Length: 0\n" +
                            '\n'
            );
        }

        private int findTarget(String path) {
            for (ForwardingRule rule : forwardingRules) {
                if (rule.matches(path)) {
                    return rule.getPort();
                }
            }
            return -1;
        }

        private static String extractPath(StringBuffer httpRequest) {
            int idx1 = httpRequest.indexOf(" ");
            if (idx1 != -1) {
                int idx2 = httpRequest.indexOf(" ", idx1 + 1);
                if (idx2 != -1) {
                    return httpRequest.substring(idx1 + 1, idx2);
                }
            }
            return null;
        }
    }
}

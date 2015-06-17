package com.netflix.eureka2.testkit.netrouter.internal;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * Internal abstraction that represents a single port proxied by a router.
 *
 * @author Tomasz Bak
 */
public class RouterPort {

    private final int targetPort;
    private final int localPort;
    private final NetworkLinkImpl link;
    private final RxClient<ByteBuf, ByteBuf> rxClient;

    private volatile RxServer<ByteBuf, ByteBuf> rxServer;
    private final Set<ObservableConnection<?, ?>> pendingConnections = Collections.newSetFromMap(new ConcurrentHashMap<ObservableConnection<?, ?>, Boolean>());

    public RouterPort(int targetPort) {
        this.targetPort = targetPort;
        this.link = new NetworkLinkImpl();
        rxClient = RxNetty.<ByteBuf, ByteBuf>newTcpClientBuilder("localhost", targetPort).build();
        link.linkEvents().subscribe(
                new Action1<LinkEvent>() {
                    @Override
                    public void call(LinkEvent linkEvent) {
                        handleLinkUpdates(linkEvent);
                    }
                }
        );
        openPort(0);
        this.localPort = rxServer.getServerPort();
    }

    private void handleLinkUpdates(LinkEvent linkEvent) {
        if (link.isUp()) {
            openPort(localPort);
        } else {
            closePort();
        }
    }

    public int getLocalPort() {
        return localPort;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public NetworkLink getLink() {
        return link;
    }

    public void shutdown() {
        closePort();
    }

    private void openPort(int port) {
        if (rxServer != null) {
            return;
        }
        rxServer = RxNetty.newTcpServerBuilder(port, new ConnectionHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(ObservableConnection<ByteBuf, ByteBuf> newConnection) {
                return bridgeConnection(newConnection);
            }
        }).build().start();
    }

    private void closePort() {
        if (rxServer != null) {
            try {
                rxServer.shutdown();
            } catch (InterruptedException ignore) {
            }
            rxServer = null;
            for (ObservableConnection<?, ?> connection : pendingConnections) {
                connection.close();
            }
            pendingConnections.clear();
        }
    }

    private Observable<Void> bridgeConnection(final ObservableConnection<ByteBuf, ByteBuf> clientConnection) {
        pendingConnections.add(clientConnection);

        // FIXME Using reply subject is a memory leak
        final ReplaySubject<ByteBuf> forwardingSubject = ReplaySubject.create();

        Observable<Void> clientToServerForwarder = clientConnection.getInput().flatMap(
                new Func1<ByteBuf, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ByteBuf byteBuf) {
                        forwardingSubject.onNext(byteBuf.retain());
                        return Observable.empty();
                    }
                });

        Observable<Void> serverToClient = rxClient.connect().flatMap(
                new Func1<ObservableConnection<ByteBuf, ByteBuf>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final ObservableConnection<ByteBuf, ByteBuf> targetConnection) {
                        forwardingSubject.subscribe(new Subscriber<ByteBuf>() {
                            @Override
                            public void onCompleted() {
                                targetConnection.close();
                            }

                            @Override
                            public void onError(Throwable e) {
                                targetConnection.close();
                            }

                            @Override
                            public void onNext(ByteBuf byteBuf) {
                                targetConnection.writeAndFlush(byteBuf);
                            }
                        });

                        return targetConnection.getInput().flatMap(new Func1<ByteBuf, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(ByteBuf byteBuf) {
                                byteBuf.retain();
                                return clientConnection.writeAndFlush(byteBuf);
                            }
                        }).doOnError(new Action1<Throwable>() {
                            @Override
                            public void call(Throwable e) {
                                clientConnection.close();
                            }
                        });
                    }
                });
        return Observable.merge(clientToServerForwarder, serverToClient)
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        forwardingSubject.onCompleted();
                        pendingConnections.remove(clientConnection);
                    }
                });
    }
}

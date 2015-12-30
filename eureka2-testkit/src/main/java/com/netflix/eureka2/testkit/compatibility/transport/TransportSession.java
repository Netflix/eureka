/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.testkit.compatibility.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.channel.*;
import com.netflix.eureka2.spi.model.*;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import com.netflix.eureka2.testkit.netrouter.NetworkRouters;
import rx.Observable;
import rx.Subscription;

/**
 */
public class TransportSession {

    private final EurekaClientTransportFactory clientTransportFactory;

    private final InstanceModel instanceModel;
    private final InterestModel interestModel;
    private final TransportModel transportModel;

    private final ClientHello clientHello;
    private final ReplicationClientHello replicationClientHello;
    private final ServerHello serverHello;
    private final ReplicationServerHello replicationServerHello;

    private final TestableRegistrationTransportHandler registrationAcceptor = new TestableRegistrationTransportHandler();
    private final TestableInterestTransportHandler interestAcceptor = new TestableInterestTransportHandler();
    private final TestableReplicationTransportHandler replicationAcceptor = new TestableReplicationTransportHandler();

    private final Subscription serverSubscription;
    private final int targetPort;
    private final Server eurekaServer;
    private final NetworkRouter networkRouter;

    public TransportSession(EurekaClientTransportFactory clientTransportFactory,
                            EurekaServerTransportFactory serverTransportFactory,
                            boolean withNetworkRouter) throws InterruptedException {
        this.clientTransportFactory = clientTransportFactory;
        this.instanceModel = InstanceModel.getDefaultModel();
        this.interestModel = InterestModel.getDefaultModel();
        this.transportModel = TransportModel.getDefaultModel();

        this.clientHello = transportModel.newClientHello(instanceModel.createSource(Source.Origin.LOCAL, "testClient", 1));
        this.replicationClientHello = transportModel.newReplicationClientHello(instanceModel.createSource(Source.Origin.LOCAL, "replicationClient", 1), 1);

        Source serverSource = instanceModel.createSource(Source.Origin.LOCAL, "testServer", 1);
        this.serverHello = transportModel.newServerHello(serverSource);
        this.replicationServerHello = transportModel.newReplicationServerHello(serverSource);

        ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory = new ChannelPipelineFactory<InstanceInfo, InstanceInfo>() {
            @Override
            public Observable<ChannelPipeline<InstanceInfo, InstanceInfo>> createPipeline() {
                return Observable.just(new ChannelPipeline<>("registration", registrationAcceptor));
            }
        };
        ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory = new ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
                return Observable.just(new ChannelPipeline<>("interest", interestAcceptor));
            }
        };
        ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory = new ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void>() {
            @Override
            public Observable<ChannelPipeline<ChangeNotification<InstanceInfo>, Void>> createPipeline() {
                return Observable.just(new ChannelPipeline<>("replication", replicationAcceptor));
            }
        };

        BlockingQueue<EurekaServerTransportFactory.ServerContext> serverContextQueue = new LinkedBlockingQueue<>();

        this.serverSubscription = serverTransportFactory.connect(0, registrationPipelineFactory, interestPipelineFactory, replicationPipelineFactory)
                .doOnNext(context -> serverContextQueue.add(context))
                .doOnError(e -> e.printStackTrace())
                .subscribe();
        EurekaServerTransportFactory.ServerContext serverContext = serverContextQueue.poll(30, TimeUnit.SECONDS);

        this.targetPort = serverContext.getPort();

        if (withNetworkRouter) {
            this.networkRouter = withNetworkRouter ? NetworkRouters.aRouter() : null;
            int proxyPort = networkRouter.bridgeTo(targetPort);
            this.eurekaServer = new Server("localhost", proxyPort);
        } else {
            this.networkRouter = null;

            this.eurekaServer = new Server("localhost", targetPort);
        }
    }

    public InstanceModel getInstanceModel() {
        return instanceModel;
    }

    public InterestModel getInterestModel() {
        return interestModel;
    }

    public TransportModel getTransportModel() {
        return transportModel;
    }

    public ClientHello getClientHello() {
        return clientHello;
    }

    public ReplicationClientHello getReplicationClientHello() {
        return replicationClientHello;
    }

    public ServerHello getServerHello() {
        return serverHello;
    }

    public ReplicationServerHello getReplicationServerHello() {
        return replicationServerHello;
    }

    public TestableRegistrationTransportHandler getRegistrationAcceptor() {
        return registrationAcceptor;
    }

    public TestableInterestTransportHandler getInterestAcceptor() {
        return interestAcceptor;
    }

    public TestableReplicationTransportHandler getReplicationAcceptor() {
        return replicationAcceptor;
    }

    public void shutdown() {
        if (serverSubscription != null) {
            serverSubscription.unsubscribe();
        }
    }

    public RegistrationHandler createRegistrationClient() {
        RegistrationHandler handler = clientTransportFactory.newRegistrationClientTransport(eurekaServer);
        ChannelPipeline<InstanceInfo, InstanceInfo> pipeline = new ChannelPipeline<>("testRegistrationClient", handler);
        handler.init(new ChannelContext<>(pipeline, null));
        return handler;
    }

    public InterestHandler createInterestClient() {
        InterestHandler handler = clientTransportFactory.newInterestTransport(eurekaServer);
        ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> pipeline = new ChannelPipeline<>("testInterestClient", handler);
        handler.init(new ChannelContext<>(pipeline, null));
        return handler;
    }

    public ReplicationHandler createReplicationClient() {
        ReplicationHandler handler = clientTransportFactory.newReplicationTransport(eurekaServer);
        ChannelPipeline<ChangeNotification<InstanceInfo>, Void> pipeline = new ChannelPipeline<>("testReplicationClient", handler);
        handler.init(new ChannelContext<>(pipeline, null));
        return handler;
    }

    public void injectNetworkFailure() {
        if (networkRouter == null) {
            throw new IllegalStateException("NetworkRouter not present");
        }
        networkRouter.getLinkTo(targetPort).disconnect(5, TimeUnit.SECONDS);
    }

    class TestableRegistrationTransportHandler implements RegistrationHandler {

        private volatile int activeConnectionCount;

        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
            return Observable.create(subscriber -> {
                registrationUpdates
                        .doOnSubscribe(() -> activeConnectionCount++)
                        .doOnUnsubscribe(() -> activeConnectionCount--)
                        .subscribe(
                                next -> {
                                    if (next.getKind() == ChannelNotification.Kind.Hello) {
                                        subscriber.onNext(ChannelNotification.<ServerHello, InstanceInfo>newHello(serverHello));
                                    } else {
                                        subscriber.onNext(next);
                                    }
                                },
                                e -> e.printStackTrace()
                        );
            });
        }

        public int getActiveConnectionCount() {
            return activeConnectionCount;
        }
    }

    class TestableInterestTransportHandler implements InterestHandler {

        private final BlockingQueue<Interest<InstanceInfo>> interestUpdates = new LinkedBlockingQueue<>();

        private final List<ChangeNotification<InstanceInfo>> replyStream = new ArrayList<>();
        private volatile int activeConnectionCount;

        @Override
        public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> interests) {
            return Observable.create(subscriber -> {
                interests
                        .doOnNext(interest -> {
                            switch (interest.getKind()) {
                                case Hello:
                                    ChannelNotification<ChangeNotification<InstanceInfo>> serverHelloNotification = ChannelNotification.newHello(serverHello);
                                    subscriber.onNext(serverHelloNotification);
                                    break;
                                case Heartbeat:
                                    subscriber.onNext(ChannelNotification.<ChangeNotification<InstanceInfo>>newHeartbeat());
                                    break;
                                case Data:
                                    interestUpdates.add(interest.getData());
                                    ChangeNotification<InstanceInfo> bufferStart = StreamStateNotification.bufferStartNotification(interest.getData());
                                    ChangeNotification<InstanceInfo> bufferEnd = StreamStateNotification.bufferEndNotification(interest.getData());
                                    subscriber.onNext(ChannelNotification.newData(bufferStart));
                                    for (ChangeNotification<InstanceInfo> reply : replyStream) {
                                        subscriber.onNext(ChannelNotification.newData(reply));
                                    }
                                    subscriber.onNext(ChannelNotification.newData(bufferEnd));
                            }
                        })
                        .doOnError(e -> e.printStackTrace())
                        .doOnSubscribe(() -> activeConnectionCount++)
                        .doOnUnsubscribe(() -> activeConnectionCount--)
                        .subscribe();
            });
        }

        public Interest<InstanceInfo> getLastInterest() throws InterruptedException {
            return interestUpdates.poll(5, TimeUnit.SECONDS);
        }

        public void setReplyStream(ChangeNotification<InstanceInfo>... changeNotifications) {
            replyStream.clear();
            Collections.addAll(replyStream, changeNotifications);
        }

        public int getActiveConnectionCount() {
            return activeConnectionCount;
        }
    }

    class TestableReplicationTransportHandler implements ReplicationHandler {

        private final BlockingQueue<ChangeNotification<InstanceInfo>> replicationUpdates = new LinkedBlockingQueue<>();

        @Override
        public void init(ChannelContext<ChangeNotification<InstanceInfo>, Void> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<Void>> handle(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> inputStream) {
            return Observable.create(subscriber -> {
                AtomicReference<Source> clientSourceRef = new AtomicReference<Source>();
                inputStream
                        .doOnNext(replicationNotification -> {
                            switch (replicationNotification.getKind()) {
                                case Hello:
                                    ChannelNotification<Void> serverHelloNotification = ChannelNotification.newHello(replicationServerHello);
                                    clientSourceRef.set(serverHelloNotification.getHello());
                                    subscriber.onNext(serverHelloNotification);
                                    break;
                                case Heartbeat:
                                    subscriber.onNext(ChannelNotification.<Void>newHeartbeat());
                                    break;
                                case Data:
                                    replicationUpdates.add(replicationNotification.getData());
                            }
                        })
                        .doOnError(e -> e.printStackTrace())
                        .subscribe();
            });
        }

        public ChangeNotification<InstanceInfo> takeNextUpdate() throws InterruptedException {
            return replicationUpdates.poll(5, TimeUnit.SECONDS);
        }
    }
}

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

package com.netflix.eureka2.transport.server;

import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.transport.client.EurekaPipelineConfigurator;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.server.RxServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 */
public class StdEurekaServerTransportFactory extends EurekaServerTransportFactory {

    private static final Logger logger = LoggerFactory.getLogger(StdEurekaServerTransportFactory.class);

    @Override
    public Observable<ServerContext> connect(int port,
                                             ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory,
                                             ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory,
                                             ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory) {
        return Observable.create(subscriber -> {

            // Shutting down server port, does not enforce pending connection shutdown. To force it, each connection
            // is watching shutdownHook observable, and closes itself when this observable terminates.
            PublishSubject<Void> shutdownHook = PublishSubject.create();

            RxServer<Object, Object> rxServer = RxNetty.createTcpServer(
                    port,
                    new EurekaPipelineConfigurator(),
                    new EurekaConnectionHandler(registrationPipelineFactory, interestPipelineFactory, replicationPipelineFactory, shutdownHook)
            ).start();

            PublishSubject<ServerContext> responseSubject = PublishSubject.create();

            responseSubject.doOnUnsubscribe(() -> {
                logger.info("Connection unsubscribed; shutting down RxServer on port {}", rxServer.getServerPort());
                try {
                    rxServer.shutdown();
                    shutdownHook.onCompleted();
                } catch (InterruptedException e) {
                    logger.error("Server shutdown interrupted");
                }
            }).subscribe(subscriber);

            responseSubject.onNext(new ServerContext() {
                @Override
                public int getPort() {
                    return rxServer.getServerPort();
                }
            });
        });
    }

    static class EurekaConnectionHandler implements ConnectionHandler<Object, Object> {

        private final ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory;
        private final ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory;
        private final ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory;
        private final Observable<Void> shutdownHook;

        EurekaConnectionHandler(ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory,
                                ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory,
                                ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory,
                                Observable<Void> shutdownHook) {
            this.registrationPipelineFactory = registrationPipelineFactory;
            this.interestPipelineFactory = interestPipelineFactory;
            this.replicationPipelineFactory = replicationPipelineFactory;
            this.shutdownHook = shutdownHook;
        }

        @Override
        public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
            logger.debug("Subscribed to StdEurekaServerTransportFactory handler");

            PublishSubject<ProtocolMessageEnvelope> outputSubject = PublishSubject.create();
            Observable<Void> output = outputSubject.flatMap(envelope -> connection.writeAndFlush(envelope));

            AtomicReference<TransportService> session = new AtomicReference<>();

            shutdownHook.doOnTerminate(() -> connection.close()).subscribe();

            // Connect input
            Observable<Void> input = connection.getInput()
                    .doOnNext(next -> {
                        ProtocolMessageEnvelope envelope = (ProtocolMessageEnvelope) next;
                        if (session.get() == null) {
                            switch (envelope.getProtocolType()) {
                                case Registration:
                                    session.set(new RegistrationTransportService(registrationPipelineFactory, outputSubject));
                                    break;
                                case Interest:
                                    session.set(new InterestTransportService(interestPipelineFactory, outputSubject));
                                    break;
                                case Replication:
                                    session.set(new ReplicationTransportService(replicationPipelineFactory, outputSubject));
                                    break;
                            }
                        }
                        session.get().handleInput(envelope);
                    })
                    .ignoreElements()
                    .cast(Void.class)
                    .doOnUnsubscribe(() -> {
                        if (session.get() != null) {
                            session.get().terminateInput();
                        }
                    });

            return Observable.merge(output, input);
        }
    }
}

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

package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.channel.LoggingChannelHandler;
import com.netflix.eureka2.channel.OutputChangeNotificationSourcingHandler;
import com.netflix.eureka2.channel.SourceIdGenerator;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.channel.interest.InterestClientHandshakeHandler;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import rx.Observable;

/**
 * Snapshot style registry fetch. There are no retries if the subscription fails. It is up to the client to
 * implement proper retry policy on the returned observable. Heartbeats are also not sent, as the connection
 * will be immediately disconnected one registry content is downloaded.
 * <h1>Timeouts</h1>
 * For slow link connection, it may happen that the server side heartbeat timeout will be triggered followed by server disconnect.
 * Adding heartbeating will not solve the issue however, until back-pressure is implemented, as heartbeat reply message
 * would be queued up on the server side, and ultimately cause timeout on the client. Backpressure will provide for
 * interleaving of registry data with control traffic.
 */
public class SnapshotInterestClient implements EurekaInterestClient {

    private final Source clientSource;
    private final ServerResolver serverResolver;
    private final EurekaClientTransportFactory transportFactory;
    private final SourceIdGenerator idGenerator;

    public SnapshotInterestClient(Source clientSource,
                                  ServerResolver serverResolver,
                                  EurekaClientTransportFactory transportFactory) {
        this.clientSource = clientSource;
        this.serverResolver = serverResolver;
        this.transportFactory = transportFactory;
        this.idGenerator = new SourceIdGenerator();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        Observable<ChannelNotification<Interest<InstanceInfo>>> interestAll = Observable.just(
                ChannelNotification.newData(Interests.forFullRegistry())).concatWith(Observable.never()
        );

        return createPipeline()
                .take(1)
                .flatMap(pipeline -> pipeline.getFirst().handle(interestAll))
                .filter(channelNotification -> {
                    if (channelNotification.getKind() != ChannelNotification.Kind.Data) {
                        return false;
                    }
                    ChangeNotification<InstanceInfo> change = channelNotification.getData();
                    if (change.getKind() != ChangeNotification.Kind.BufferSentinel) {
                        return true;
                    }
                    StreamStateNotification<InstanceInfo> bufferChange = (StreamStateNotification<InstanceInfo>) change;
                    return bufferChange.getBufferState() == StreamStateNotification.BufferState.BufferEnd;
                })
                .map(channelNotification -> channelNotification.getData())
                .takeUntil(change -> change.getKind() == ChangeNotification.Kind.BufferSentinel);
    }

    @Override
    public void shutdown() {
    }

    private Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
        return serverResolver.resolve().map(server -> {
                    String pipelineId = "interest[client=" + clientSource.getName() + ",server=" + server;
                    return new ChannelPipeline<>(pipelineId,
                            new OutputChangeNotificationSourcingHandler(),
                            new InterestClientHandshakeHandler(clientSource, idGenerator),
                            new LoggingChannelHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>(LoggingChannelHandler.LogLevel.INFO),
                            transportFactory.newInterestTransport(server)
                    );
                }
        );
    }
}

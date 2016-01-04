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

package com.netflix.eureka2.ext.grpc.transport.client;

import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.grpc.Eureka2ReplicationGrpc;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.model.ReplicationClientHello;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.*;

/**
 */
public class GrpcReplicationClientTransportHandler implements ReplicationHandler {

    private static final Logger logger = LoggerFactory.getLogger(GrpcReplicationClientTransportHandler.class);

    private final Eureka2ReplicationGrpc.Eureka2Replication replicationService;

    public GrpcReplicationClientTransportHandler(Eureka2ReplicationGrpc.Eureka2Replication replicationService) {
        this.replicationService = replicationService;
    }

    @Override
    public void init(ChannelContext<ChangeNotification<InstanceInfo>, Void> channelContext) {
    }

    @Override
    public Observable<ChannelNotification<Void>> handle(Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> updates) {
        return Observable.create(subscriber -> {

            logger.debug("Subscribed to GrpcReplicationClientTransportHandler handler");

            StreamObserver<Eureka2.GrpcReplicationRequest> updateObserver = replicationService.subscribe(new StreamObserver<Eureka2.GrpcReplicationResponse>() {
                @Override
                public void onNext(Eureka2.GrpcReplicationResponse notification) {
                    logger.debug("Received response of type {}", notification.getItemCase());
                    subscriber.onNext(toChannelNotification(notification));
                }

                @Override
                public void onError(Throwable t) {
                    logger.debug("Received onError from reply channel", t);
                    subscriber.onError(t);
                }

                @Override
                public void onCompleted() {
                    logger.debug("Received onCompleted from reply channel");
                    subscriber.onCompleted();
                }
            });

            updates.subscribe(
                    update -> updateObserver.onNext(toGrpcReplicationRequest(update)),
                    e -> updateObserver.onError(e),
                    () -> updateObserver.onCompleted()
            );
        });
    }

    private static Eureka2.GrpcReplicationRequest toGrpcReplicationRequest(ChannelNotification<ChangeNotification<InstanceInfo>> update) {
        switch (update.getKind()) {
            case Heartbeat:
                return Eureka2.GrpcReplicationRequest.newBuilder().setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance()).build();
            case Hello:
                ReplicationClientHello hello = update.getHello();
                return Eureka2.GrpcReplicationRequest.newBuilder().setClientHello(toGrpcReplicationClientHello(hello)).build();
            case Data:
                return Eureka2.GrpcReplicationRequest.newBuilder().setChangeNotification(toGrpcChangeNotification(update.getData())).build();
        }
        throw new IllegalStateException("Unrecognized channel notification type " + update.getKind());
    }

    private static ChannelNotification<Void> toChannelNotification(Eureka2.GrpcReplicationResponse notification) {
        switch (notification.getItemCase()) {
            case HEARTBEAT:
                return ChannelNotification.newHeartbeat();
            case SERVERHELLO:
                return ChannelNotification.newHello(toReplicationServerHello(notification.getServerHello()));
        }
        throw new IllegalStateException("Unrecognized channel notification type " + notification.getItemCase());
    }
}



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

package com.netflix.eureka2.ext.grpc.transport.server;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.grpc.Eureka2ReplicationGrpc;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.channel.ClientHello;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.toChangeNotification;

/**
 */
public class GrpcEureka2ReplicationServerImpl implements Eureka2ReplicationGrpc.Eureka2Replication {

    private static final Logger logger = LoggerFactory.getLogger(GrpcEureka2ReplicationServerImpl.class);

    private final ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory;

    public GrpcEureka2ReplicationServerImpl(ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory) {
        this.replicationPipelineFactory = replicationPipelineFactory;
    }

    @Override
    public StreamObserver<Eureka2.GrpcReplicationRequest> subscribe(StreamObserver<Eureka2.GrpcReplicationResponse> responseObserver) {
        logger.debug("Replication channel subscription accepted from transport");

        ReplicationSession session = new ReplicationSession(responseObserver);

        return new StreamObserver<Eureka2.GrpcReplicationRequest>() {
            @Override
            public void onNext(Eureka2.GrpcReplicationRequest grpcReplicationRequest) {
                logger.debug("Forwarding replication request of type {}", grpcReplicationRequest.getItemCase());
                session.onNext(grpcReplicationRequest);
            }

            @Override
            public void onError(Throwable t) {
                logger.debug("Forwarding error from transport ({})", t.getMessage());
                session.onError(t);
            }

            @Override
            public void onCompleted() {
                logger.debug("Forwarding onCompleted from transport");
                session.onCompleted();
            }
        };
    }

    private class ReplicationSession {
        private final StreamObserver<Eureka2.GrpcReplicationResponse> responseObserver;
        private final ChannelPipeline<ChangeNotification<InstanceInfo>, Void> pipeline;
        private final PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> replicationSubject = PublishSubject.create();
        private final Subscription pipelineSubscription;

        private final Map<String, InstanceInfo> instanceCache = new ConcurrentHashMap<>();

        private ReplicationSession(StreamObserver<Eureka2.GrpcReplicationResponse> responseObserver) {
            this.responseObserver = responseObserver;
            this.pipeline = replicationPipelineFactory.createPipeline().take(1).toBlocking().first();
            this.pipelineSubscription = connectPipeline();
        }

        public void onNext(Eureka2.GrpcReplicationRequest grpcReplicationRequest) {
            Eureka2.GrpcReplicationRequest.ItemCase kind = grpcReplicationRequest.getItemCase();
            switch (kind) {
                case CLIENTHELLO:
                    ClientHello clientHello = GrpcModelConverters.toReplicationClientHello(grpcReplicationRequest.getClientHello());
                    replicationSubject.onNext(ChannelNotification.newHello(clientHello));
                    break;
                case HEARTBEAT:
                    replicationSubject.onNext(ChannelNotification.newHeartbeat());
                    break;
                case CHANGENOTIFICATION:
                    replicationSubject.onNext(ChannelNotification.newData(toChangeNotification(grpcReplicationRequest.getChangeNotification(), instanceCache)));
                    break;
                default:
                    logger.error("Unrecognized replication request notification type {}", kind);
                    onError(new IOException("Unrecognized replication request notification type " + kind));
            }
        }

        public void onError(Throwable e) {
            replicationSubject.onError(e);
            pipelineSubscription.unsubscribe();
        }

        public void onCompleted() {
            replicationSubject.onCompleted();
            pipelineSubscription.unsubscribe();
        }

        private Subscription connectPipeline() {
            return pipeline.getFirst().handle(replicationSubject)
                    .doOnUnsubscribe(() -> logger.debug("Replication pipeline unsubscribed"))
                    .subscribe(
                            next -> {
                                logger.debug("Sending channel notification to client {}", next.getKind());
                                responseObserver.onNext(convert(next));
                            },
                            e -> {
                                logger.debug("Send onError to transport ({})", e.getMessage());
                                responseObserver.onError(e);
                            },
                            () -> {
                                logger.debug("Send onCompleted to transport ({})");
                                responseObserver.onCompleted();
                            }
                    );
        }

        private Eureka2.GrpcReplicationResponse convert(ChannelNotification<Void> channelNotification) {
            switch (channelNotification.getKind()) {
                case Hello:
                    return Eureka2.GrpcReplicationResponse.newBuilder().setServerHello(
                            GrpcModelConverters.toGrpcReplicationServerHello(channelNotification.getHello())
                    ).build();
                case Heartbeat:
                    return Eureka2.GrpcReplicationResponse.newBuilder().setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance()).build();
                case Disconnected:
                    // Ignore
                    break;
            }
            throw new IllegalStateException("Unrecognized channel notification kind " + channelNotification.getKind());
        }
    }
}

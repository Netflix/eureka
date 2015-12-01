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

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.grpc.Eureka2InterestGrpc;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.ClientHello;
import com.netflix.eureka2.spi.model.ServerHello;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.subjects.PublishSubject;

import java.io.IOException;

import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.toInterest;

/**
 */
class GrpcEureka2InterestServerImpl implements Eureka2InterestGrpc.Eureka2Interest {

    private static final Logger logger = LoggerFactory.getLogger(GrpcEureka2InterestServerImpl.class);

    private final ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory;
    private final Eureka2.GrpcServerHello grpcServerHello;

    GrpcEureka2InterestServerImpl(ServerHello serverHello,
                                  ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory) {
        this.interestPipelineFactory = interestPipelineFactory;
        this.grpcServerHello = GrpcModelConverters.toGrpcServerHello(serverHello);
    }

    @Override
    public StreamObserver<Eureka2.GrpcInterestRequest> subscribe(StreamObserver<Eureka2.GrpcInterestResponse> responseObserver) {
        logger.debug("Interest channel subscription accepted from transport");

        InterestSession session = new InterestSession(responseObserver);

        return new StreamObserver<Eureka2.GrpcInterestRequest>() {
            @Override
            public void onNext(Eureka2.GrpcInterestRequest grpcInterestRequest) {
                logger.debug("Forwarding interest request of type {}", grpcInterestRequest.getItemCase());
                session.onNext(grpcInterestRequest);
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

    class InterestSession {
        private final StreamObserver<Eureka2.GrpcInterestResponse> responseObserver;
        private final ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> pipeline;
        private final PublishSubject<ChannelNotification<Interest<InstanceInfo>>> interestSubject = PublishSubject.create();

        InterestSession(StreamObserver<Eureka2.GrpcInterestResponse> responseObserver) {
            this.responseObserver = responseObserver;
            this.pipeline = interestPipelineFactory.createPipeline().take(1).toBlocking().first();
            connectPipeline();
        }

        void onNext(Eureka2.GrpcInterestRequest grpcInterestRequest) {
            Eureka2.GrpcInterestRequest.ItemCase kind = grpcInterestRequest.getItemCase();
            switch (kind) {
                case CLIENTHELLO:
                    ClientHello clientHello = GrpcModelConverters.toClientHello(grpcInterestRequest.getClientHello());
                    interestSubject.onNext(ChannelNotification.newHello(clientHello));
                    break;
                case HEARTBEAT:
                    interestSubject.onNext(ChannelNotification.newHeartbeat());
                    break;
                case INTERESTREGISTRATION:
                    interestSubject.onNext(ChannelNotification.newData(toInterest(grpcInterestRequest.getInterestRegistration())));
                    break;
                default:
                    logger.error("Unrecognized interest request notification type {}", kind);
                    onError(new IOException("Unrecognized interest request notification type " + kind));
            }
        }

        void onError(Throwable t) {
            interestSubject.onError(t);
        }

        void onCompleted() {
            interestSubject.onCompleted();
        }

        private void connectPipeline() {
            pipeline.getFirst().handle(interestSubject).subscribe(
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

        private Eureka2.GrpcInterestResponse convert(ChannelNotification<ChangeNotification<InstanceInfo>> channelNotification) {
            switch (channelNotification.getKind()) {
                case Hello:
                    return Eureka2.GrpcInterestResponse.newBuilder().setServerHello(grpcServerHello).build();
                case Heartbeat:
                    return Eureka2.GrpcInterestResponse.newBuilder().setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance()).build();
                case Data:
                    return Eureka2.GrpcInterestResponse.newBuilder().setChangeNotification(
                            GrpcModelConverters.toGrpcChangeNotification(channelNotification.getData())
                    ).build();
                case Disconnected:
                    // Ignore
                    break;

            }
            throw new IllegalStateException("Unrecognized channel notification kind " + channelNotification.getKind());
        }
    }
}

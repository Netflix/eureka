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

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.grpc.Eureka2RegistrationGrpc;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipeline;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.ClientHello;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.toInstanceInfo;

/**
 */
class GrpcEureka2RegistrationServerImpl implements Eureka2RegistrationGrpc.Eureka2Registration {

    private static final Logger logger = LoggerFactory.getLogger(GrpcEureka2RegistrationServerImpl.class);

    private final ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory;

    GrpcEureka2RegistrationServerImpl(ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory) {
        this.registrationPipelineFactory = registrationPipelineFactory;
    }

    @Override
    public StreamObserver<Eureka2.GrpcRegistrationRequest> register(StreamObserver<Eureka2.GrpcRegistrationResponse> responseObserver) {
        logger.debug("Registration channel subscription accepted from transport");

        RegistrationSession session = new RegistrationSession(responseObserver);

        return new StreamObserver<Eureka2.GrpcRegistrationRequest>() {
            @Override
            public void onNext(Eureka2.GrpcRegistrationRequest grpcRegistrationRequest) {
                logger.debug("Forwarding registration request of type {}", grpcRegistrationRequest.getOneofRequestCase());
                session.onNext(grpcRegistrationRequest);
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

    class RegistrationSession {
        private final StreamObserver<Eureka2.GrpcRegistrationResponse> responseObserver;
        private final ChannelPipeline<InstanceInfo, InstanceInfo> pipeline;
        private final PublishSubject<ChannelNotification<InstanceInfo>> registrationSubject = PublishSubject.create();
        private final Subscription pipelineSubscription;

        RegistrationSession(StreamObserver<Eureka2.GrpcRegistrationResponse> responseObserver) {
            this.responseObserver = responseObserver;
            this.pipeline = registrationPipelineFactory.createPipeline().take(1).toBlocking().first();
            this.pipelineSubscription = connectPipeline();
        }

        void onNext(Eureka2.GrpcRegistrationRequest grpcRegistrationRequest) {
            Eureka2.GrpcRegistrationRequest.OneofRequestCase kind = grpcRegistrationRequest.getOneofRequestCase();
            switch (kind) {
                case CLIENTHELLO:
                    ClientHello clientHello = GrpcModelConverters.toClientHello(grpcRegistrationRequest.getClientHello());
                    registrationSubject.onNext(ChannelNotification.newHello(clientHello));
                    break;
                case HEARTBEAT:
                    registrationSubject.onNext(ChannelNotification.newHeartbeat());
                    break;
                case INSTANCEINFO:
                    registrationSubject.onNext(ChannelNotification.newData(toInstanceInfo(grpcRegistrationRequest.getInstanceInfo())));
                    break;
                default:
                    logger.error("Unrecognized registration request notification type {}", kind);
                    onError(new IOException("Unrecognized registration request notification type " + kind));
            }
        }

        void onError(Throwable t) {
            registrationSubject.onError(t);
            pipelineSubscription.unsubscribe();
        }

        void onCompleted() {
            registrationSubject.onCompleted();
            pipelineSubscription.unsubscribe();
        }

        private Subscription connectPipeline() {
            return pipeline.getFirst().handle(registrationSubject)
                    .doOnUnsubscribe(() -> logger.debug("Registration pipeline unsubscribed"))
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

        private Eureka2.GrpcRegistrationResponse convert(ChannelNotification<InstanceInfo> channelNotification) {
            switch (channelNotification.getKind()) {
                case Hello:
                    return Eureka2.GrpcRegistrationResponse.newBuilder().setServerHello(
                            GrpcModelConverters.toGrpcServerHello(channelNotification.getHello())
                    ).build();
                case Heartbeat:
                    return Eureka2.GrpcRegistrationResponse.newBuilder().setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance()).build();
                case Data:
                    return Eureka2.GrpcRegistrationResponse.newBuilder().setAck(Eureka2.GrpcAcknowledgement.getDefaultInstance()).build();
                case Disconnected:
                    // Ignore
                    break;
            }
            throw new IllegalStateException("Unrecognized channel notification kind " + channelNotification.getKind());
        }
    }
}

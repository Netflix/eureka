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

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.grpc.Eureka2RegistrationGrpc;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.toGrpcClientHello;
import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.toGrpcInstanceInfo;

/**
 */
public class GrpcRegistrationClientTransportHandler implements RegistrationHandler {

    private static final Logger logger = LoggerFactory.getLogger(GrpcRegistrationClientTransportHandler.class);

    private final Eureka2RegistrationGrpc.Eureka2Registration registrationService;

    public GrpcRegistrationClientTransportHandler(Eureka2RegistrationGrpc.Eureka2Registration registrationService) {
        this.registrationService = registrationService;
    }

    @Override
    public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
    }

    @Override
    public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
        return Observable.create(subscriber -> {
                    logger.debug("Subscribed to GrpcRegistrationClientTransportHandler handler");

                    Queue<InstanceInfo> updatesQueue = new ConcurrentLinkedQueue<InstanceInfo>();

                    StreamObserver<Eureka2.GrpcRegistrationRequest> submittedRegistrationsObserver = registrationService.register(
                            new StreamObserver<Eureka2.GrpcRegistrationResponse>() {
                                @Override
                                public void onNext(Eureka2.GrpcRegistrationResponse response) {
                                    logger.debug("Received response of type {}", response.getItemCase());
                                    switch (response.getItemCase()) {
                                        case HEARTBEAT:
                                            subscriber.onNext(ChannelNotification.<InstanceInfo>newHeartbeat());
                                            break;
                                        case SERVERHELLO:
                                            ChannelNotification<InstanceInfo> helloReply = ChannelNotification.newHello(GrpcModelConverters.toServerHello(response.getServerHello()));
                                            subscriber.onNext(helloReply);
                                            break;
                                        case ACK:
                                            InstanceInfo confirmedUpdate = updatesQueue.poll();
                                            if (confirmedUpdate != null) {
                                                subscriber.onNext(ChannelNotification.newData(confirmedUpdate));
                                            } else {
                                                subscriber.onError(new IllegalStateException("Received unexpected acknowledgement in the registration channel"));
                                            }
                                            break;
                                        default:
                                            subscriber.onError(new IllegalStateException("Unexpected response kind " + response.getItemCase()));
                                    }
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

                    Subscription subscription = registrationUpdates
                            .doOnUnsubscribe(() -> {
                                logger.debug("Unsubscribing from registration update input stream");
                                submittedRegistrationsObserver.onCompleted();
                            })
                            .subscribe(
                                    registrationNotification -> {
                                        logger.debug("Forwarding notification of type {} over the transport", registrationNotification.getKind());
                                        if (registrationNotification.getKind() == ChannelNotification.Kind.Data) {
                                            updatesQueue.add(registrationNotification.getData());
                                        }
                                        submittedRegistrationsObserver.onNext(toGrpcRegistrationRequest(registrationNotification));
                                    },
                                    e -> {
                                        logger.debug("Forwarding error from registration update stream to transport ({})", e.getMessage());
                                        submittedRegistrationsObserver.onError(e);
                                    },
                                    () -> {
                                        logger.debug("Forwarding onComplete from registration update stream to transport");
                                        submittedRegistrationsObserver.onCompleted();
                                    }
                            );
                    subscriber.add(subscription);
                }
        );
    }

    private static Eureka2.GrpcRegistrationRequest toGrpcRegistrationRequest(ChannelNotification<InstanceInfo> registrationNotification) {
        switch (registrationNotification.getKind()) {
            case Heartbeat:
                return Eureka2.GrpcRegistrationRequest.newBuilder().setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance()).build();
            case Hello:
                return Eureka2.GrpcRegistrationRequest.newBuilder().setClientHello(toGrpcClientHello(registrationNotification.getHello())).build();
            case Data:
                return Eureka2.GrpcRegistrationRequest.newBuilder().setInstanceInfo(toGrpcInstanceInfo(registrationNotification.getData())).build();
        }
        throw new IllegalStateException("Unrecognized channel notification type " + registrationNotification.getKind());
    }
}

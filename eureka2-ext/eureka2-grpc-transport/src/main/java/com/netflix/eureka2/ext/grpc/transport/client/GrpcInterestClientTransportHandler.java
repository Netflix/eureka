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
import com.netflix.eureka2.grpc.Eureka2InterestGrpc;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelContext;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.netflix.eureka2.ext.grpc.model.GrpcModelConverters.*;

public class GrpcInterestClientTransportHandler implements InterestHandler {

    private static final Logger logger = LoggerFactory.getLogger(GrpcInterestClientTransportHandler.class);

    private final Eureka2InterestGrpc.Eureka2Interest interestService;
    private final AtomicLong channelCounter = new AtomicLong();

    public GrpcInterestClientTransportHandler(Eureka2InterestGrpc.Eureka2Interest interestService) {
        this.interestService = interestService;
    }

    @Override
    public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
    }

    @Override
    public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> interests) {
        return Observable.create(subscriber -> {
                    Map<String, InstanceInfo> instanceCache = new HashMap<>();

                    logger.debug("Subscribed to GrpcInterestClientTransportHandler handler");

                    StreamObserver<Eureka2.GrpcInterestRequest> interestObserver = interestService.subscribe(new StreamObserver<Eureka2.GrpcInterestResponse>() {
                        @Override
                        public void onNext(Eureka2.GrpcInterestResponse notification) {
                            logger.debug("Received response of type {}", notification.getItemCase());
                            ChannelNotification<ChangeNotification<InstanceInfo>> change = toChannelNotification(notification, instanceCache);
                            if (change != null) {
                                subscriber.onNext(change);
                            } else {
                                logger.warn("Ignoring notification, as it cannot be fully reconstructed {}", notification.getItemCase());
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

                    interests.subscribe(
                            interest -> interestObserver.onNext(toGrpcInterestRequest(interest)),
                            e -> interestObserver.onError(e),
                            () -> interestObserver.onCompleted()
                    );
                }
        );
    }

    private static Eureka2.GrpcInterestRequest toGrpcInterestRequest(ChannelNotification<Interest<InstanceInfo>> interest) {
        switch (interest.getKind()) {
            case Heartbeat:
                return Eureka2.GrpcInterestRequest.newBuilder().setHeartbeat(Eureka2.GrpcHeartbeat.getDefaultInstance()).build();
            case Hello:
                return Eureka2.GrpcInterestRequest.newBuilder().setClientHello(toGrpcClientHello(interest.getHello())).build();
            case Data:
                return Eureka2.GrpcInterestRequest.newBuilder().setInterestRegistration(toGrpcInterestRegistration(interest.getData())).build();
        }
        throw new IllegalStateException("Unrecognized channel notification type " + interest.getKind());
    }

    private static ChannelNotification<ChangeNotification<InstanceInfo>> toChannelNotification(Eureka2.GrpcInterestResponse notification,
                                                                                               Map<String, InstanceInfo> instanceCache) {
        switch (notification.getItemCase()) {
            case HEARTBEAT:
                return ChannelNotification.newHeartbeat();
            case SERVERHELLO:
                return ChannelNotification.newHello(toServerHello(notification.getServerHello()));
            case CHANGENOTIFICATION:
                ChangeNotification<InstanceInfo> change = toChangeNotification(notification.getChangeNotification(), instanceCache);
                return change == null ? null : ChannelNotification.newData(change);
        }
        throw new IllegalStateException("Unrecognized channel notification type " + notification.getItemCase());
    }
}

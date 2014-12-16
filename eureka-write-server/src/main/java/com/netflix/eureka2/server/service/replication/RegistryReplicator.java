/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.server.service.replication;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.channel.ReplicationChannel;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class RegistryReplicator {

    private static final Logger logger = LoggerFactory.getLogger(RegistryReplicator.class);

    private final String ownInstanceId;
    private final EurekaServerRegistry<InstanceInfo> registry;

    private ReplicationChannel channel;
    private Subscription subscription;

    RegistryReplicator(String ownInstanceId,
                       EurekaServerRegistry<InstanceInfo> registry) {
        this.ownInstanceId = ownInstanceId;
        this.registry = registry;
    }

    public void reconnect(final ReplicationChannel delegateChannel) {
        if (subscription != null) {
            subscription.unsubscribe();
        }

        if (channel != null) {
            channel.close();
        }

        channel = delegateChannel;

        subscription = channel.hello(new ReplicationHello(ownInstanceId, registry.size()))
                .flatMap(new Func1<ReplicationHelloReply, Observable<ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<ChangeNotification<InstanceInfo>> call(ReplicationHelloReply replicationHelloReply) {
                        if (replicationHelloReply.getSourceId().equals(ownInstanceId)) {
                            logger.info("{}: Taking out replication connection to itself", ownInstanceId);
                            return Observable.empty();
                        }
                        logger.info("{} received hello back from {}", ownInstanceId, replicationHelloReply.getSourceId());
                        return registry.forInterest(Interests.forFullRegistry(), Source.localSource());
                    }
                }).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        logger.info("{}: Replication change notification stream closed", ownInstanceId);
                        channel.close();
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("{}: Registry interest stream terminated with an error", ownInstanceId, e);
                        channel.close();
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> changeNotification) {
                        switch (changeNotification.getKind()) {
                            case Add:
                                subscribeToTransportSend(channel.register(changeNotification.getData()), "register request");
                                break;
                            case Modify:
                                subscribeToTransportSend(channel.update(changeNotification.getData()), "update request");
                                break;
                            case Delete:
                                subscribeToTransportSend(channel.unregister(changeNotification.getData().getId()), "delete request");
                                break;
                        }
                    }
                });
    }

    public void close() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    protected void subscribeToTransportSend(Observable<Void> sendResult, final String what) {
        sendResult.subscribe(new Action1<Void>() {
            @Override
            public void call(Void aVoid) {
                // Nothing to do for a void.
            }
        }, new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                logger.warn("{}: Failed to send " + what + " request to the server. Closing the channel.", ownInstanceId);
                channel.close();
            }
        });
    }
}

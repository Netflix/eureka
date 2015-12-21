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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.Heartbeat;
import com.netflix.eureka2.spi.model.ReplicationClientHello;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.transport.ProtocolConverters;
import com.netflix.eureka2.transport.codec.ProtocolMessageEnvelope;
import com.netflix.eureka2.transport.codec.ProtocolMessageEnvelope.ProtocolType;
import rx.Observable;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.transport.codec.ProtocolMessageEnvelope.replicationEnvelope;

/**
 */
public class ReplicationTransportService implements TransportService {

    private final PublishSubject<ChannelNotification<ChangeNotification<InstanceInfo>>> inputSubject = PublishSubject.create();
    private final Map<String, InstanceInfo> instanceCache = new HashMap<>();

    ReplicationTransportService(ChannelPipelineFactory<ChangeNotification<InstanceInfo>, Void> replicationPipelineFactory,
                                PublishSubject<ProtocolMessageEnvelope> outputSubject) {
        replicationPipelineFactory.createPipeline().take(1).flatMap(pipeline -> {
            return pipeline.getFirst().handle(inputSubject).flatMap(replyNotification -> {
                Observable<ProtocolMessageEnvelope> envelope;
                try {
                    switch (replyNotification.getKind()) {
                        case Hello:
                            envelope = Observable.just(replicationEnvelope(replyNotification.getHello()));
                            break;
                        case Heartbeat:
                            envelope = Observable.just(replicationEnvelope(TransportModel.getDefaultModel().creatHeartbeat()));
                            break;
                        case Data:
                            envelope = Observable.error(new IllegalStateException("Data notifications not expected"));
                            break;
                        default:
                            return Observable.error(new IllegalStateException("Unrecognized envelope kind " + replyNotification.getKind()));
                    }
                } catch (Exception e) {
                    return Observable.error(e);
                }
                return envelope;
            });
        }).subscribe(outputSubject);
    }

    @Override
    public void handleInput(ProtocolMessageEnvelope envelope) {
        if (envelope.getProtocolType() != ProtocolType.Replication) {
            inputSubject.onError(new IOException("Non-replication protocol message " + envelope.getProtocolType()));
            return;
        }

        Object message = envelope.getMessage();
        if (message instanceof Heartbeat) {
            inputSubject.onNext(ChannelNotification.newHeartbeat());
        } else if (message instanceof ReplicationClientHello) {
            inputSubject.onNext(ChannelNotification.newHello(message));
        } else {
            try {
                inputSubject.onNext(ProtocolConverters.asChannelNotification(envelope, instanceCache));
            } catch (Exception e) {
                inputSubject.onError(e);
            }
        }
    }
}

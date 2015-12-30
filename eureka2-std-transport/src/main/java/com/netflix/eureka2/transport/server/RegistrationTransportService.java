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

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.ClientHello;
import com.netflix.eureka2.spi.protocol.common.GoAway;
import com.netflix.eureka2.spi.model.Heartbeat;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.protocol.ProtocolMessageEnvelope;
import rx.Observable;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.protocol.ProtocolMessageEnvelope.registrationEnvelope;

/**
 */
class RegistrationTransportService implements TransportService {

    private final PublishSubject<ChannelNotification<InstanceInfo>> inputSubject = PublishSubject.create();

    RegistrationTransportService(ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory,
                                 PublishSubject<ProtocolMessageEnvelope> outputSubject) {
        registrationPipelineFactory.createPipeline().take(1).flatMap(pipeline -> {
            return pipeline.getFirst().handle(inputSubject).flatMap(replyNotification -> {
                ProtocolMessageEnvelope envelope;
                try {
                    switch (replyNotification.getKind()) {
                        case Hello:
                            envelope = registrationEnvelope(replyNotification.getHello());
                            break;
                        case Heartbeat:
                            envelope = registrationEnvelope(TransportModel.getDefaultModel().creatHeartbeat());
                            break;
                        case Data:
                            envelope = registrationEnvelope(TransportModel.getDefaultModel().createAcknowledgement());
                            break;
                        default:
                            return Observable.error(new IllegalStateException("Unrecognized envelope kind " + replyNotification.getKind()));
                    }
                } catch (Exception e) {
                    return Observable.error(e);
                }
                return Observable.just(envelope);
            });
        }).subscribe(outputSubject);
    }

    @Override
    public void handleInput(ProtocolMessageEnvelope envelope) {
        if (envelope.getProtocolType() != ProtocolMessageEnvelope.ProtocolType.Registration) {
            inputSubject.onError(new IOException("Non-registration protocol message " + envelope.getProtocolType()));
            return;
        }

        Object message = envelope.getMessage();
        if (message instanceof GoAway) {
            terminateInput();
        } else if (message instanceof Heartbeat) {
            inputSubject.onNext(ChannelNotification.newHeartbeat());
        } else if (message instanceof ClientHello) {
            inputSubject.onNext(ChannelNotification.newHello(message));
        } else if (message instanceof InstanceInfo) {
            inputSubject.onNext(ChannelNotification.newData((InstanceInfo) message));
        } else {
            inputSubject.onError(new IOException("Unexpected message of type " + message.getClass().getName()));
        }
    }

    @Override
    public void terminateInput() {
        inputSubject.onCompleted();
    }
}

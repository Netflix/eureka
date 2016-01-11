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
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.model.ChannelModel;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.model.channel.ClientHello;
import com.netflix.eureka2.spi.model.channel.Heartbeat;
import com.netflix.eureka2.spi.model.transport.InterestRegistration;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope.ProtocolType;
import com.netflix.eureka2.transport.ProtocolConverters;
import rx.Observable;
import rx.subjects.PublishSubject;


/**
 */
public class InterestTransportService implements TransportService {
    private final PublishSubject<ChannelNotification<Interest<InstanceInfo>>> inputSubject = PublishSubject.create();

    InterestTransportService(ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory,
                             PublishSubject<ProtocolMessageEnvelope> outputSubject) {
        interestPipelineFactory.createPipeline().take(1).flatMap(pipeline -> {
            return pipeline.getFirst().handle(inputSubject).flatMap(replyNotification -> {
                Observable<ProtocolMessageEnvelope> envelope;
                try {
                    switch (replyNotification.getKind()) {
                        case Hello:
                            envelope = Observable.just(TransportModel.getDefaultModel().interestEnvelope(replyNotification.getHello()));
                            break;
                        case Heartbeat:
                            envelope = Observable.just(TransportModel.getDefaultModel().interestEnvelope(ChannelModel.getDefaultModel().newHeartbeat()));
                            break;
                        case Data:
                            try {
                                envelope = Observable.just(ProtocolConverters.asProtocolEnvelope(ProtocolType.Interest, replyNotification.getData()));
                            } catch (Exception e) {
                                envelope = Observable.error(e);
                            }
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
        if (envelope.getProtocolType() != ProtocolType.Interest) {
            inputSubject.onError(new IOException("Non-interest protocol message " + envelope.getProtocolType()));
            return;
        }

        Object message = envelope.getMessage();
        if (message instanceof Heartbeat) {
            inputSubject.onNext(ChannelNotification.newHeartbeat());
        } else if (message instanceof ClientHello) {
            inputSubject.onNext(ChannelNotification.newHello(message));
        } else if (message instanceof InterestRegistration) {
            InterestRegistration ir = (InterestRegistration) message;
            Interest<InstanceInfo> interestUpdate = ir.getInterests().length == 1
                    ? ir.getInterests()[0]
                    : Interests.forSome(ir.getInterests());
            inputSubject.onNext(ChannelNotification.newData(interestUpdate));
        } else {
            inputSubject.onError(new IOException("Unexpected message of type " + message.getClass().getName()));
        }
    }

    @Override
    public void terminateInput() {
        inputSubject.onCompleted();
    }
}

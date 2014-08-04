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

package com.netflix.eureka.client.transport.discovery.protocol.asynchronous;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.transport.discovery.DiscoveryClient;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.registry.Interest;
import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.utils.HeartBeatHandler;
import com.netflix.eureka.transport.utils.HeartBeatHandler.HeartbeatClient;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class AsyncDiscoveryClient implements DiscoveryClient {

    private final MessageBroker messageBroker;
    private final HeartbeatClient<Heartbeat> heartbeatClient;

    public AsyncDiscoveryClient(MessageBroker messageBroker, long heartbeatInterval, TimeUnit heartbeatUnit) {
        this.messageBroker = messageBroker;
        heartbeatClient = new HeartBeatHandler.HeartbeatClient<Heartbeat>(messageBroker, heartbeatInterval, heartbeatUnit) {
            @Override
            protected Heartbeat heartbeatMessage() {
                return Heartbeat.INSTANCE;
            }
        };
    }

    @Override
    public Observable<Void> registerInterestSet(List<Interest> interests) {
        return executeCommand(new UserContent(new RegisterInterestSet(interests)));
    }

    @Override
    public Observable<Void> unregisterInterestSet() {
        return executeCommand(new UserContent(new UnregisterInterestSet()));
    }

    @Override
    public Observable<InterestSetNotification> updates() {
        return messageBroker.incoming().filter(new Func1<Message, Boolean>() {
            @Override
            public Boolean call(Message message) {
                // FIXME What shall we do about unexpected messages?
                return message instanceof UserContent && ((UserContent) message).getContent() instanceof InterestSetNotification;
            }
        }).map(new Func1<Message, InterestSetNotification>() {
            @Override
            public InterestSetNotification call(Message message) {
                return (InterestSetNotification) ((UserContent) message).getContent();
            }
        });
    }

    @Override
    public void shutdown() {
        heartbeatClient.shutdown();
        messageBroker.shutdown();
    }

    private Observable<Void> executeCommand(UserContent command) {
        Observable<Acknowledgement> ack = messageBroker.submitWithAck(command);
        return ack.map(new Func1<Acknowledgement, Void>() {
            @Override
            public Void call(Acknowledgement acknowledgement) {
                return null;
            }
        });
    }
}

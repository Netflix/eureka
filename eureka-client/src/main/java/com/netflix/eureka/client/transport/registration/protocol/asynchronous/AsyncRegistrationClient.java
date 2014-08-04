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

package com.netflix.eureka.client.transport.registration.protocol.asynchronous;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.utils.HeartBeatHandler;
import com.netflix.eureka.transport.utils.HeartBeatHandler.HeartbeatClient;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class AsyncRegistrationClient implements RegistrationClient {
    private final MessageBroker messageBroker;
    private final HeartbeatClient<Heartbeat> heartbeatClient;

    public AsyncRegistrationClient(MessageBroker messageBroker, long heatbeatInterval, TimeUnit heartbeatUnit) {
        this.messageBroker = messageBroker;
        heartbeatClient = new HeartBeatHandler.HeartbeatClient<Heartbeat>(messageBroker, heatbeatInterval, heartbeatUnit) {
            @Override
            protected Heartbeat heartbeatMessage() {
                return Heartbeat.INSTANCE;
            }
        };
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return executeCommand(new UserContent(instanceInfo));
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo, Update update) {
        return executeCommand(new UserContent(update));
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return executeCommand(new UserContent(new Unregister()));
    }

    @Override
    public void shutdown() {
        heartbeatClient.shutdown();
        messageBroker.shutdown();
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return heartbeatClient.connectionStatus();
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

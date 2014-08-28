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

package com.netflix.eureka.client.transport.registration.asynchronous;

import com.netflix.eureka.client.transport.registration.RegistrationClient;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class AsyncRegistrationClient implements RegistrationClient {
    private final MessageBroker messageBroker;

    public AsyncRegistrationClient(MessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return messageBroker.submitWithAck(new Register(instanceInfo));
    }

    @Override
    public Observable<Void> update(InstanceInfo instanceInfo) {
        return messageBroker.submitWithAck(new Update(instanceInfo));
    }

    @Override
    public Observable<Void> unregister(InstanceInfo instanceInfo) {
        return messageBroker.submitWithAck(new Unregister());
    }

    @Override
    public Observable<Void> heartbeat(InstanceInfo instanceInfo) {
        return messageBroker.submit(Heartbeat.INSTANCE);
    }

    @Override
    public void shutdown() {
        messageBroker.shutdown();
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return null;
    }

}

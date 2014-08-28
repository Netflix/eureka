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

package com.netflix.eureka.client.transport.registration;

import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 * Client registration protocol.
 * Heartbeats are not relevant for application layer so are handled underneath.
 *
 * @author Tomasz Bak
 */
public interface RegistrationClient {

    Observable<Void> register(InstanceInfo instanceInfo);

    Observable<Void> update(InstanceInfo instanceInfo);

    Observable<Void> unregister(InstanceInfo instanceInfo);

    Observable<Void> heartbeat(InstanceInfo instanceInfo);

    void shutdown();

    /**
     * Application must subscribe to this to know when communication channel is broken, and client
     * must reconnect. Failed heartbeats will be visible only via this observable.
     */
    Observable<Void> lifecycleObservable();
}

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

package com.netflix.eureka.server.transport.registration;

import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.Context;
import rx.Observable;

/**
 * A server side mirror interface of {@code RegistrationClient} interface from eureka-client module.
 * Single instance is shared between all client connections. A particular client can be identified
 * from {@link Context} object.
 *
 * @author Tomasz Bak
 */
public interface RegistrationHandler {

    Observable<Void> register(Context context, InstanceInfo instanceInfo);

    Observable<Void> update(Context context, Update update);

    Observable<Void> unregister(Context context);

    Observable<Void> heartbeat(Context context);

    void shutdown();
}

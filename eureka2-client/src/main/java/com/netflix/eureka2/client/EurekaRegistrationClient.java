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

package com.netflix.eureka2.client;

import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

/**
 * An Eureka client that support registering registrants to remote Eureka servers. Multiple registrants can
 * use the same client for remote server registration.
 *
 * Once registered, the client is responsible for maintaining persisted heartbeating connections with the remote
 * server to maintain the registration until the registrant explicitly unregisters.
 *
 * @author David Liu
 */
public interface EurekaRegistrationClient {

    /**
     * Return a {@link RegistrationObservable} that when subscribes to, initiates registration with the remote server
     * based on the InstanceInfos received. Changes between InstanceInfos will be applied as updates to the initial
     * registration. InstanceInfo Ids cannot change for InstanceInfos within an input stream.
     *
     * @param registrant an Observable that emits new InstanceInfos for the registrant each time it needs to be
     *                   updated. Initial registrations is predicated on two conditions, the returned
     *                   RegistrationRequest must be subscribed to, and an initial InstanceInfo must be emitted
     *                   by the input observable.
     * @return {@link RegistrationObservable}
     */
    RegistrationObservable register(Observable<InstanceInfo> registrant);

    /**
     * shutdown and clean up all resources for this client
     */
    void shutdown();

}

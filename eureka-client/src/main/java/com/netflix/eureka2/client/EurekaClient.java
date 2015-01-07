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

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * A client for connecting to eureka servers.
 *
 * Use {@link Eureka} to create concrete instances of this class.
 *
 * <h2>Registration</h2>
 *
 * When configured with an appropriate {@link ServerResolver} for resolving eureka write servers, this client can be
 * used to register one or more instances with eureka.
 *
 * <h2>Registry read</h2>
 *
 * When configured with an appropriate {@link ServerResolver} for resolving eureka write servers, this client can be
 * used to discovery instances in the eureka registry for different {@link Interest}.
 *
 * <h4>Interest Streams</h4>
 *
 * Any registry information fetched by this client is expressed as an infinite stream of {@link ChangeNotification}, this
 * stream will complete only on shutdown of this client.
 * In case, a stream completes with an error, the caller must retry the subscription to the returned stream.
 *
 * @author Nitesh Kant
 */
public abstract class EurekaClient {

    /**
     * Register or update an instance with eureka.
     *
     * @param instanceInfo Instance to register.
     *
     * @return An {@link Observable} representing an acknowledgment for the registration.
     */
    public abstract Observable<Void> register(InstanceInfo instanceInfo);

    /**
     * Unregister the passed instance from eureka. If the instance was not registered, this call is ignored.
     *
     * @param instanceInfo Instance to unregister.
     *
     * @return An {@link Observable} representing an acknowledgment for the unregistration.
     */
    public abstract Observable<Void> unregister(InstanceInfo instanceInfo);

    /**
     * Creates a stream of {@link ChangeNotification} for the passed interest.
     *
     * @param interest Interest for the registry.
     *
     * @return Stream of {@link ChangeNotification} for the passed interest.
     */
    public abstract Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest);

    /**
     * Creates a stream of {@link ChangeNotification} for the passed application.
     *
     * @param appName Application name of interest.
     *
     * @return Stream of {@link ChangeNotification} for the passed application.
     */
    public abstract Observable<ChangeNotification<InstanceInfo>> forApplication(String appName);

    /**
     * Creates a stream of {@link ChangeNotification} for the passed vips.
     *
     * @param vips Vips of interest.
     *
     * @return Stream of {@link ChangeNotification} for the passed vips.
     */
    public abstract Observable<ChangeNotification<InstanceInfo>> forVips(String... vips);

    /**
     * Closes this client.
     */
    public abstract void close();
}

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

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * An eureka client that support reading interested data from remote eureka servers.
 *
 * @author David Liu
 */
public interface EurekaInterestClient {

    /**
     * Return an observable that when subscribed to, will emit an infinite stream of {@link ChangeNotification} of
     * the Interested items. This stream will only onComplete on client shutdown, and can be retried when onError.
     *
     * @param interest an Interest specifying the matching criteria for InstanceInfos. See {@link Interests}
     *                 for methods to construct interests.
     * @return an observable of {@link ChangeNotification}s of the interested InstanceInfos. The notifications
     *         returned may contain interleaved data notifications and streamState notifications. Standard
     *         transformers for transforming the returned stream are available at
     *         {@link com.netflix.eureka2.client.functions.InterestFunctions}.
     */
    Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest);

    /**
     * shutdown and clean up all resources for this client
     */
    void shutdown();

}

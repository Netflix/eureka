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

package com.netflix.eureka2.registry;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import rx.Observable;

/**
 * Base interface for Eureka Registries
 *
 * @author Nitesh Kant
 */
public interface EurekaRegistry<T> {

    /**
     * @return a boolean to denote whether the register action successfully added a new entry or updated an existing entry
     */
    Observable<Boolean> register(T instanceInfo);

    /**
     * @return a boolean to denote whether the update action successfully added a new entry
     */
    Observable<Boolean> unregister(T instanceInfo);

    int size();

    Observable<T> forSnapshot(Interest<T> interest);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest);

    Observable<Void> shutdown();

    /**
     * Shuts down the registry. All the interest client subscriptions are terminated
     * with an error, where the error value is the provided parameter.
     *
     * @param cause error to propagate to subscription clients
     */
    Observable<Void> shutdown(Throwable cause);
}

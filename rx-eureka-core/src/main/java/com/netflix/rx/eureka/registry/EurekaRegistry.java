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

package com.netflix.rx.eureka.registry;

import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interest;
import rx.Observable;

import java.util.Set;

/**
 * @author Nitesh Kant
 */
public interface EurekaRegistry<T> {

    /**
     * Each entry in a registry is associated with exactly one origin:
     * <ul>
     * <li>{@link #LOCAL}</li> - there is an opened registration client connection to the write local server
     * <li>{@link #REPLICATED}</li> - replicated entry from another server
     * </ul>
     */
    enum Origin { LOCAL, REPLICATED }

    Observable<Void> register(T instanceInfo);

    Observable<Void> register(T instanceInfo, Origin origin);

    Observable<Void> unregister(String instanceId);

    Observable<Void> update(T updatedInfo, Set<Delta<?>> deltas);

    Observable<T> forSnapshot(Interest<T> interest);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest, Origin origin);

    Observable<Void> shutdown();
}

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

package com.netflix.eureka.registry;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import rx.Observable;

import java.util.Set;

/**
 * @author Nitesh Kant
 */
public interface EurekaRegistry<T> {

    DataCenterInfo getRegistryLocation();

    Observable<Void> register(T instanceInfo);

    Observable<Void> unregister(String instanceId);

    Observable<Void> update(T updatedInfo, Set<Delta<?>> deltas);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest);

    Observable<Void> shutdown();
}

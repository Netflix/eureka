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

import com.netflix.rx.eureka.data.Source;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interest;
import rx.Observable;

import java.util.Set;

/**
 * @author Nitesh Kant
 */
public interface EurekaRegistry<T> {

    Observable<Void> register(T instanceInfo);

    Observable<Void> register(T instanceInfo, Source source);

    Observable<Void> unregister(String instanceId);

    Observable<Void> unregister(String instanceId, Source source);

    Observable<Void> update(T updatedInfo, Set<Delta<?>> deltas);

    Observable<Void> update(T updatedInfo, Set<Delta<?>> deltas, Source source);

    Observable<T> forSnapshot(Interest<T> interest);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest);

    Observable<ChangeNotification<T>> forInterest(Interest<T> interest, Source source);

    Observable<Void> shutdown();
}

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

package com.netflix.eureka;

import com.netflix.eureka.datastore.Item;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import rx.Observable;

import com.netflix.eureka.registry.InstanceInfo;

/**
 *
 *
 * @author Nitesh Kant
 */
public abstract class EurekaClient {

    public abstract Observable<Void> update(InstanceInfo instanceInfo);

    public abstract Observable<ChangeNotification<? extends Item>> forInterest(Interest<InstanceInfo> interest);

    public abstract Observable<ChangeNotification<? extends Item>> forVips(String... vips);

    public abstract Observable<Void> close();
}

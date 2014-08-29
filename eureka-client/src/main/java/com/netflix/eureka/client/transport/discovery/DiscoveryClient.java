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

package com.netflix.eureka.client.transport.discovery;

import com.netflix.eureka.datastore.Item;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

import java.util.List;

/**
 * @author Tomasz Bak
 */
public interface DiscoveryClient {

    Observable<Void> registerInterestSet(Interest<InstanceInfo> interest);

    Observable<Void> unregisterInterestSet();

    Observable<ChangeNotification<? extends Item>> updates();

    Observable<Void> heartbeat();

    void shutdown();
}

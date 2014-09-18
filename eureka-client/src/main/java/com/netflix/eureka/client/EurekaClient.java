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

package com.netflix.eureka.client;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;

/**
 *
 *
 * @author Nitesh Kant
 */
public abstract class EurekaClient {

    public static final String READ_SERVER_RESOLVER_NAME = "eureka_read_server_resolver";

    public static final String WRITE_SERVER_RESOLVER_NAME = "eureka_write_server_resolver";

    public abstract Observable<Void> register(InstanceInfo instanceInfo);

    public abstract Observable<Void> update(InstanceInfo instanceInfo);

    public abstract Observable<Void> unregister(InstanceInfo instanceInfo);

    public abstract Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest);

    public abstract Observable<ChangeNotification<InstanceInfo>> forApplication(String appName);

    public abstract Observable<ChangeNotification<InstanceInfo>> forVips(String... vips);

    public abstract void close();
}

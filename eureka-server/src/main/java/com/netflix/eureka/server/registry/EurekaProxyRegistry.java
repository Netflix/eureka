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

package com.netflix.eureka.server.registry;

import java.util.Set;

import com.google.inject.Inject;
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.DataCenterInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import rx.Observable;

/**
 * Registry implemented on top of eureka-client. It does not story anything, just
 * provides an adapter from {@link EurekaClient} to {@link EurekaRegistry} interface.
 * Server side {@link InterestChannel} is binded to real registry on write server,
 * and to proxy registry (this class) for read server.
 *
 * <p>
 * All th registration related methods throw an exception, as thay are not
 * relevent for the proxy. Our class hierarchy provides single abstraction for
 * registrations and interests, and it is not possible to get one without the other.
 *
 * <h1>Why not the same registry as used internally by {@link EurekaClient}?</h1>
 *
 * {@link EurekaClient} contract and failure recovery mode is aplicable to
 * read server endpoint.
 *
 * @author Tomasz Bak
 */
public class EurekaProxyRegistry implements EurekaRegistry<InstanceInfo> {

    private final EurekaClient eurekaClient;

    @Inject
    public EurekaProxyRegistry(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    @Override
    public DataCenterInfo getRegistryLocation() {
        throw new IllegalStateException("method not supported by EurekaProxyRegistry");
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        throw new IllegalStateException("method not supported by EurekaProxyRegistry");
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        throw new IllegalStateException("method not supported by EurekaProxyRegistry");
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        throw new IllegalStateException("method not supported by EurekaProxyRegistry");
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return eurekaClient.forInterest(interest);
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.empty();
    }
}

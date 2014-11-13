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

package com.netflix.eureka2.server;

import com.google.inject.Inject;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.Source;
import com.netflix.eureka2.service.InterestChannel;
import com.netflix.eureka2.service.ServiceChannel;
import rx.Observable;

import java.util.Set;

/**
 * Registry implemented on top of eureka-client. It does not story anything, just
 * provides an adapter from {@link EurekaClient} to {@link EurekaRegistry} interface.
 * Server side {@link InterestChannel} is bound to real registry on write server,
 * and to proxy registry (this class) for read server.
 *
 * <p>
 * All the registration related methods throw an exception, as they are not
 * relevant for the proxy. Our class hierarchy provides single abstraction for
 * registrations and interests, and it is not possible to get one without the other.
 *
 * <h1>Why not the same registry as used internally by {@link EurekaClient}?</h1>
 *
 * This registry is used by the {@link ServiceChannel}s on the server end of a read server which must always get its
 * data from a write server which in this case is fetched by the normal {@link EurekaClient}
 *
 * @author Tomasz Bak
 */
public class EurekaReadServerRegistry implements EurekaServerRegistry<InstanceInfo> {

    private final EurekaClient eurekaClient;

    @Inject
    public EurekaReadServerRegistry(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    @Override
    public Observable<Status> register(InstanceInfo instanceInfo) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Status> register(InstanceInfo instanceInfo, Source source) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Status> unregister(InstanceInfo instanceInfo) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Status> unregister(InstanceInfo instanceInfo, Source source) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Status> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Status> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas, Source source) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public int size() {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        throw new IllegalStateException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return eurekaClient.forInterest(interest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source source) {
        throw new IllegalStateException("Origin filtering not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.empty();
    }

    @Override
    public String toString() {
        return eurekaClient.toString();
    }
}

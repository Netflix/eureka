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

package com.netflix.eureka2.server.registry;

import javax.inject.Singleton;

import com.google.inject.Inject;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistryView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * Registry implemented on top of eureka-client. It does not story anything, just
 * provides an adapter from {@link EurekaInterestClient} to {@link com.netflix.eureka2.registry.EurekaRegistry} interface.
 *
 * <p>
 * All the registration related methods throw an exception, as they are not
 * relevant for the proxy. Our class hierarchy provides single abstraction for
 * registrations and interests, and it is not possible to get one without the other.
 *
 * <h1>Why not the same registry as used internally by {@link EurekaInterestClient}?</h1>
 *
 * This registry is used on the server end of a read server which must always get its
 * data from a write server which in this case is fetched by the normal {@link EurekaInterestClient}
 *
 * @author Tomasz Bak
 */
@Singleton
public class EurekaReadServerRegistryView implements EurekaRegistryView<InstanceInfo> {
    private static final Logger logger = LoggerFactory.getLogger(EurekaReadServerRegistryView.class);

    private final EurekaInterestClient interestClient;

    @Inject
    public EurekaReadServerRegistryView(EurekaInterestClient interestClient) {
        this.interestClient = interestClient;
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest) {
        throw new UnsupportedOperationException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
        throw new UnsupportedOperationException("method not supported by EurekaReadServerRegistry");
    }

    /**
     * This class emits buffer start/end markers used internally by the interest channels/transport.
     */
    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        return interestClient.forInterest(interest);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
        throw new UnsupportedOperationException("Origin filtering not supported by EurekaReadServerRegistry");
    }

    @Override
    public String toString() {
        return interestClient.toString();
    }
}

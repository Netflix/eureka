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

import java.util.ArrayList;
import java.util.List;

import com.google.inject.Inject;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.channel.ServiceChannel;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.functions.Func1;

/**
 * Registry implemented on top of eureka-client. It does not story anything, just
 * provides an adapter from {@link EurekaClient} to {@link SourcedEurekaRegistry} interface.
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
public class EurekaReadServerRegistry implements SourcedEurekaRegistry<InstanceInfo> {

    private final EurekaInterestClient interestClient;

    @Inject
    public EurekaReadServerRegistry(EurekaInterestClient interestClient) {
        this.interestClient = interestClient;
    }

    @Override
    public Observable<Boolean> register(InstanceInfo instanceInfo, Source source) {
        throw new UnsupportedOperationException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo instanceInfo, Source source) {
        throw new UnsupportedOperationException("method not supported by EurekaReadServerRegistry");
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("method not supported by EurekaReadServerRegistry");
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
        return interestClient.forInterest(interest).flatMap(bufferStartEndDelineateFun(interest));
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest, Source.SourceMatcher sourceMatcher) {
        throw new UnsupportedOperationException("Origin filtering not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Long> evictAllExcept(Source.SourceMatcher retainMatcher) {
        throw new UnsupportedOperationException("evictAllExcept not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<? extends MultiSourcedDataHolder<InstanceInfo>> getHolders() {
        throw new UnsupportedOperationException("Origin filtering not supported by EurekaReadServerRegistry");
    }

    @Override
    public Observable<Void> shutdown() {
        return Observable.empty();
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return Observable.error(cause);
    }

    @Override
    public String toString() {
        return interestClient.toString();
    }

    static Func1<ChangeNotification<InstanceInfo>, Observable<ChangeNotification<InstanceInfo>>> bufferStartEndDelineateFun(Interest<InstanceInfo> interest) {

        final ChangeNotification<InstanceInfo> bufferStartNotification = StreamStateNotification.bufferStartNotification(interest);
        final ChangeNotification<InstanceInfo> bufferEndNotification = StreamStateNotification.bufferEndNotification(interest);

        final List<ChangeNotification<InstanceInfo>> buffer = new ArrayList<>();
        return new Func1<ChangeNotification<InstanceInfo>, Observable<ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<ChangeNotification<InstanceInfo>> call(ChangeNotification<InstanceInfo> notification) {
                if (notification.getKind() != Kind.BufferSentinel) {
                    buffer.add(notification);
                    return Observable.empty();
                }
                if (buffer.isEmpty()) {
                    return Observable.empty();
                }
                Observable<ChangeNotification<InstanceInfo>> result;
                if (buffer.size() == 1) {
                    result = Observable.just(buffer.get(0));
                } else {
                    List<ChangeNotification<InstanceInfo>> batch = new ArrayList<>(2 + buffer.size());
                    batch.add(bufferStartNotification);
                    batch.addAll(buffer);
                    batch.add(bufferEndNotification);
                    result = Observable.from(batch);
                }
                buffer.clear();
                return result;
            }
        };
    }
}

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

package com.netflix.eureka2.registry;

import com.netflix.eureka2.EurekaCloseable;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.notification.ChangeNotification;
import rx.Observable;

/**
 * @author David Liu
 */
public interface EurekaRegistry<T> extends EurekaRegistryView<T>, Sourced, EurekaCloseable {

    Observable<Void> connect(Source source, Observable<ChangeNotification<T>> registrationUpdates);

    /**
     * Evict all registry info for all sources that matches the matcher
     * @return an observable of long denoting the number of holder items touched for the eviction
     */
    Observable<Long> evictAll(Source.SourceMatcher evictionMatcher);

    Observable<? extends MultiSourcedDataHolder<T>> getHolders();

    int size();

    @Override
    Observable<Void> shutdown();

    /**
     * Shuts down the registry. All the interest client subscriptions are terminated
     * with an error, where the error value is the provided parameter.
     *
     * @param cause error to propagate to subscription clients
     */
    @Override
    Observable<Void> shutdown(Throwable cause);
}

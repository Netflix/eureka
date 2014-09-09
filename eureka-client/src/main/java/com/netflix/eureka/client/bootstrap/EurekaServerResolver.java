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

package com.netflix.eureka.client.bootstrap;

import java.net.InetSocketAddress;

import com.netflix.eureka.client.ServerResolver;
import rx.Observable;

/**
 * A resolver fetching server list from the Eureka cluster. Eureka client uses
 * this resolver to load read cluster server list from the write cluster, after the
 * registration process.
 *
 * @author Tomasz Bak
 */
public class EurekaServerResolver implements ServerResolver<InetSocketAddress> {

    @Override
    public Observable<ServerEntry<InetSocketAddress>> resolve() {
        return Observable.error(new UnsupportedOperationException("eureka server resolution not implemented."));
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }
}

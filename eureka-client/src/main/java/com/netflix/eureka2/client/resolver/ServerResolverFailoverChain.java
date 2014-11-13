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

package com.netflix.eureka2.client.resolver;

import rx.Observable;
import rx.functions.Func1;

/**
 * Provide means to use multiple sources of resolve server list.
 *
 * @author Tomasz Bak
 */
public class ServerResolverFailoverChain implements ServerResolver {

    private final Observable<Server> servers;

    public ServerResolverFailoverChain(ServerResolver... resolvers) {
        Observable<Server> chain = null;
        for (final ServerResolver resolver : resolvers) {
            if (null == chain) {
                chain = resolver.resolve();
            } else {
                chain = chain.onErrorResumeNext(new Func1<Throwable, Observable<? extends Server>>() {
                    @Override
                    public Observable<? extends Server> call(Throwable throwable) {
                        return resolver.resolve();
                    }
                });
            }
        }

        servers = chain;
    }

    @Override
    public Observable<Server> resolve() {
        return servers;
    }
}

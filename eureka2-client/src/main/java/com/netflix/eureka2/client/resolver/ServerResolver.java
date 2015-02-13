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

import com.netflix.eureka2.utils.Server;
import rx.Observable;

/**
 * A mechanism to discovery eureka servers. Each call to {@link #resolve()} returns an
 * observable with no more than one element the is the best contender for the next
 * connection.
 * <p>
 * Out of the box implementations can be created using {@link ServerResolvers}
 * <h1>Thread safety</h1>
 * Calls to {@link #resolve} method are not thread safe, as given its embedded load balancing
 * semantic sharing single resolver by multiple clients has little sense.
 *
 * @author Tomasz Bak
 */
public interface ServerResolver {

    /**
     * Returns a single element {@link Observable} of {@link Server} instances, which
     * completes immediately after the element is provided. Properly behaving implementations should
     * never complete before issuing the value. In case of a problem, a subscription should terminate
     * with an error.
     * <h1>Error handling</h1>
     * This interface does not define any specific error handling protocol, like distinguishing between
     * recoverable and non-recoverable errors.
     *
     * @return An {@link Observable} of {@link Server} instances.
     */
    Observable<Server> resolve();

    /**
     * Cleanup resources.
     */
    void close();
}

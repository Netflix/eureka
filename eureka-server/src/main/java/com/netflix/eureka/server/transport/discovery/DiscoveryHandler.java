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

package com.netflix.eureka.server.transport.discovery;

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.server.transport.Context;
import rx.Observable;

import java.util.List;

/**
 * A server side mirror interface of {@code DiscoveryClient} interface from eureka-client module.
 * Single instance is shared between all client connections. A particular client can be identified
 * from {@link Context} object.
 *
 * @author Tomasz Bak
 */
public interface DiscoveryHandler {

    Observable<Void> registerInterestSet(Context context, List<Interest> interests);

    Observable<Void> unregisterInterestSet(Context context);

    /**
     * This method will be called once for each client connection. All available
     * notifications will be forwarded to the client.
     */
    Observable<InterestSetNotification> updates(Context context);

    /**
     * Called if a connection with the remote client was terminated.
     */
    void shutdown(Context context);
}

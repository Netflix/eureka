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

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.Server;
import netflix.ocelli.LoadBalancerBuilder;
import rx.Observable;

/**
 * An implementation of {@link ServerResolver} using a static list of {@link Server} instances.
 *
 * The usage of this implementation is not recommended for production use, for which the other implementations supporting
 * dynamic server lists must be used.
 *
 * @author Tomasz Bak
 */
public class StaticServerResolver extends AbstractServerResolver {

    private final Observable<ChangeNotification<Server>> updateStream;

    public StaticServerResolver(LoadBalancerBuilder<Server> loadBalancerBuilder, Server... serverList) {
        super(loadBalancerBuilder);
        List<ChangeNotification<Server>> updates = new ArrayList<>(serverList.length);
        for (Server server : serverList) {
            updates.add(new ChangeNotification<>(Kind.Add, server));
        }
        updateStream = Observable.from(updates);
    }

    @Override
    protected Observable<ChangeNotification<Server>> serverUpdates() {
        return updateStream;
    }
}

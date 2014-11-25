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

package com.netflix.eureka2.utils;

import java.net.InetSocketAddress;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.ServiceSelector;
import netflix.ocelli.Host;
import netflix.ocelli.MembershipEvent;
import rx.Observable;
import rx.functions.Func1;

import static netflix.ocelli.MembershipEvent.EventType.*;

/**
 * Map Eureka's change notification events to Ocelli's membership events.
 *
 * @author Tomasz Bak
 */
public class EurekaOcelliEventMapper implements Func1<ChangeNotification<InstanceInfo>, Observable<MembershipEvent<InetSocketAddress>>> {

    private final ServiceSelector serviceSelector;

    public EurekaOcelliEventMapper(ServiceSelector serviceSelector) {
        this.serviceSelector = serviceSelector;
    }

    @Override
    public Observable<MembershipEvent<InetSocketAddress>> call(ChangeNotification<InstanceInfo> eurekaNotification) {
        InetSocketAddress endpoint = serviceSelector.returnServiceAddress(eurekaNotification.getData());

        Host host = new Host(endpoint.getHostString(), endpoint.getPort());
        switch (eurekaNotification.getKind()) {
            case Add:
                return Observable.just(new MembershipEvent<InetSocketAddress>(ADD, endpoint));
            case Delete:
                return Observable.just(new MembershipEvent<InetSocketAddress>(REMOVE, endpoint));
            case Modify:
                return Observable.just(
                        new MembershipEvent<InetSocketAddress>(REMOVE, endpoint),
                        new MembershipEvent<InetSocketAddress>(ADD, endpoint)
                );
            default:
                return Observable.empty();
        }
    }
}

/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.spi.channel;

import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

/**
 */
public interface RegistrationHandler extends ChannelHandler<InstanceInfo, InstanceInfo> {

    /**
     * @param registrationUpdates a stream of registration updates (at least one). A handler may make multiple
     *                            subsequent subscriptions to this observable
     * @return a stream corresponding to the registration stream with {@link InstanceInfo} objects that were
     * successfully transmitted. Actual implementation may send fewer instances, by doing compaction or
     * deduplication, however the returned stream should contain all of them in order.
     */
    @Override
    Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates);
}

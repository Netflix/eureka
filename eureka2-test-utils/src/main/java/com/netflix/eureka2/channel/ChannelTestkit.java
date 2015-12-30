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

package com.netflix.eureka2.channel;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import rx.Observable;

/**
 */
public final class ChannelTestkit {

    public static final Source CLIENT_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testClient");
    public static final Source SERVER_SOURCE = InstanceModel.getDefaultModel().createSource(Source.Origin.LOCAL, "testServer");

    public static final Interest<InstanceInfo> INTEREST = SampleInterest.DiscoveryApp.build();
    public static final InstanceInfo INSTANCE = SampleInstanceInfo.Backend.build();

    public static final ChannelNotification<InstanceInfo> CHANNEL_INSTANCE_NOTIFICATION = ChannelNotification.newData(INSTANCE);

    public static final Observable<ChannelNotification<Interest<InstanceInfo>>> CHANNEL_INTEREST_NOTIFICATION_STREAM = Observable.just(
            ChannelNotification.newData(INTEREST)
    ).mergeWith(Observable.never());

    private ChannelTestkit() {
    }

    public static <DATA> Observable<ChannelNotification<DATA>> unbound(DATA data) {
        return Observable.just(ChannelNotification.newData(data)).mergeWith(Observable.never());
    }
}

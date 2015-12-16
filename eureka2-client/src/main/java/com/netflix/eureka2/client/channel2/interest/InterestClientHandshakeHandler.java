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

package com.netflix.eureka2.client.channel2.interest;

import com.netflix.eureka2.channel2.SourceIdGenerator;
import com.netflix.eureka2.client.channel2.ClientHandshakeHandler;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelNotification;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.model.TransportModel;

/**
 */
public class InterestClientHandshakeHandler extends ClientHandshakeHandler<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> implements InterestHandler {

    private final ChannelNotification<Interest<InstanceInfo>> clientHelloNotification;

    public InterestClientHandshakeHandler(Source clientSource, SourceIdGenerator serverIdGenerator) {
        super(serverIdGenerator);
        this.clientHelloNotification = ChannelNotification.newHello(
                TransportModel.getDefaultModel().newClientHello(clientSource)
        );
    }

    @Override
    protected ChannelNotification<Interest<InstanceInfo>> createClientHello() {
        return clientHelloNotification;
    }
}

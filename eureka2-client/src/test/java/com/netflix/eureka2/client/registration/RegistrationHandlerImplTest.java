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

package com.netflix.eureka2.client.registration;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.channel.RegistrationChannel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RegistrationHandlerImplTest {

    private static final InstanceInfo DISCOVERY_1 = SampleInstanceInfo.DiscoveryServer.build();
    private static final InstanceInfo DISCOVERY_2 = SampleInstanceInfo.DiscoveryServer.build();

    private final ClientChannelFactory channelFactory = mock(ClientChannelFactory.class);
    private final RegistrationChannel registrationChannel = mock(RegistrationChannel.class);

    private RegistrationHandlerImpl registrationHandler;

    @Before
    public void setUp() throws Exception {
        when(channelFactory.newChannel()).thenReturn(registrationChannel);

        when(registrationChannel.register(any(InstanceInfo.class))).thenReturn(Observable.<Void>empty());
        when(registrationChannel.unregister()).thenReturn(Observable.<Void>empty());

        registrationHandler = new RegistrationHandlerImpl(channelFactory);
    }

    @Test(timeout = 60000)
    public void testRegistersClientsOverDifferentChannel() throws Exception {
        registrationHandler.register(DISCOVERY_1);
        verify(registrationChannel, times(1)).register(DISCOVERY_1);

        registrationHandler.register(DISCOVERY_2);
        verify(registrationChannel, times(1)).register(DISCOVERY_1);

        verify(channelFactory, times(2)).newChannel();
    }

    @Test(timeout = 60000)
    public void testSubsequentRegistrationWithChangesIfAlreadyRegistered() throws Exception {
        registrationHandler.register(DISCOVERY_1).subscribe();
        InstanceInfo update = new InstanceInfo.Builder().withInstanceInfo(DISCOVERY_1).withVipAddress("aNewName").build();
        registrationHandler.register(update).subscribe();
        verify(registrationChannel, times(1)).register(DISCOVERY_1);
        verify(registrationChannel, times(1)).register(update);
    }

    @Test(timeout = 60000)
    public void testUnregisterReleasesResources() throws Exception {
        // First register
        registrationHandler.register(DISCOVERY_1).subscribe();

        // Now unregister
        registrationHandler.unregister(DISCOVERY_1).subscribe();

        verify(registrationChannel, times(1)).unregister();
        verify(registrationChannel, times(1)).close();
    }

    @Test(timeout = 60000)
    public void testUnregistersOnShutdown() throws Exception {
        // First register
        registrationHandler.register(DISCOVERY_1).subscribe();
        verify(registrationChannel, times(1)).register(DISCOVERY_1);

        // Now shutdown the handler
        registrationHandler.shutdown();
        verify(registrationChannel, times(1)).unregister();
        verify(registrationChannel, times(1)).close();
    }
}
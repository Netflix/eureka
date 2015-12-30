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

package com.netflix.eureka2.client.interest;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class FullFetchInterestClient2Test extends AbstractInterestClientTest {

    private final EurekaRegistry<InstanceInfo> eurekaRegistry = mock(EurekaRegistry.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> registrySubject = PublishSubject.create();

    private FullFetchInterestClient client;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        setupEurekaRegistryConnect(eurekaRegistry, registrySubject);
        client = new FullFetchInterestClient(clientSource, serverResolver, transportFactory, transportConfig, eurekaRegistry, RETRY_DELAY_MS, testScheduler);
    }

    @Test
    public void testInterestSubscriptionLifecycle() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        client.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);

        // There should be immediate change notification from server
        testScheduler.triggerActions();
        assertThat(testSubscriber.takeNext(), is(bufferStartNotification()));
        assertThat(testSubscriber.takeNext(), is(addChangeNotification()));
        assertThat(testSubscriber.takeNext(), is(bufferEndNotification()));

        // Disconnect transport
        transportHandler.disconnect();
        testScheduler.advanceTimeBy(RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

        assertThat(testSubscriber.isUnsubscribed(), is(false));

        // ChangeNotification from new transport
        assertThat(testSubscriber.takeNext(), is(bufferStartNotification()));
        assertThat(testSubscriber.takeNext(), is(addChangeNotification()));
        assertThat(testSubscriber.takeNext(), is(bufferEndNotification()));
    }
}
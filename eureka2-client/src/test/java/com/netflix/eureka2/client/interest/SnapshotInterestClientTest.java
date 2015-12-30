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

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Test;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotification;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public class SnapshotInterestClientTest extends AbstractInterestClientTest {

    private final SnapshotInterestClient client = new SnapshotInterestClient(
            clientSource,
            serverResolver,
            transportFactory
    );

    @Test
    public void testForInterestReturnsFullSnapshotFollowedByOnComplete() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        client.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);

        // There should be immediate change notification from server
        assertThat(testSubscriber.takeNext(), is(addChangeNotification()));
        testSubscriber.assertOnCompleted();
    }
}
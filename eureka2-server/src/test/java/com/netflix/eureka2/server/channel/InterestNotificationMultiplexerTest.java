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

package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleDelta;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.observers.TestSubscriber;
import rx.subjects.ReplaySubject;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.utils.ExtCollections.asSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class InterestNotificationMultiplexerTest {

    @Mock
    private SourcedEurekaRegistry<InstanceInfo> registryMock;

    private InterestNotificationMultiplexer multiplexer;
    private Iterator<ChangeNotification<InstanceInfo>> notifications;

    @Before
    public void setUp() throws Exception {
        multiplexer = new InterestNotificationMultiplexer(registryMock);
        notifications = multiplexer.changeNotifications().toBlocking().getIterator();
    }

    @After
    public void tearDown() throws Exception {
        multiplexer.unregister();
    }

    @Test(timeout = 10000)
    public void testAtomicUpgrade() throws Exception {
        // Add first instance
        InstanceController controller1 = new InstanceController(SampleInstanceInfo.DiscoveryServer.build());
        upgradeTo(controller1);
        controller1.publishAdd();
        assertThat(notifications.next(), is(equalTo(controller1.addNotification)));
        assertThat(controller1.instanceSubject.hasObservers(), is(true));

        // Now remove interest for instance 1 and add interest for instance 2
        InstanceController controller2 = new InstanceController(SampleInstanceInfo.ZuulServer.build());
        upgradeTo(controller2);
        controller1.publishModify();
        controller2.publishAdd();
        assertThat(notifications.next(), is(equalTo(controller2.addNotification)));
        assertThat(controller1.instanceSubject.hasObservers(), is(false));
        assertThat(controller2.instanceSubject.hasObservers(), is(true));
    }

    @Test(timeout = 10000)
    public void testCompositeUpgrade() throws Exception {
        // Add two instances
        InstanceController controller1 = new InstanceController(SampleInstanceInfo.DiscoveryServer.build());
        InstanceController controller2 = new InstanceController(SampleInstanceInfo.ZuulServer.build());
        controller1.publishAdd();
        controller2.publishAdd();
        upgradeTo(controller1, controller2);
        assertThat(asSet(notifications.next(), notifications.next()), containsInAnyOrder(controller1.addNotification, controller2.addNotification));
        assertThat(controller1.instanceSubject.hasObservers(), is(true));
        assertThat(controller2.instanceSubject.hasObservers(), is(true));

        // Now remove interest for instance 1 and add interest for instance 3
        InstanceController controller3 = new InstanceController(SampleInstanceInfo.CliServer.build());
        upgradeTo(controller2, controller3);
        controller1.publishModify();
        controller2.publishModify();
        controller3.publishAdd();
        assertThat(asSet(notifications.next(), notifications.next()), containsInAnyOrder(controller2.modifyNotification, controller3.addNotification));
        assertThat(controller1.instanceSubject.hasObservers(), is(false));
        assertThat(controller2.instanceSubject.hasObservers(), is(true));
        assertThat(controller3.instanceSubject.hasObservers(), is(true));
    }

    @Test(timeout = 60000)
    public void testCloseUnsubcribesAllUpStreamAndOnCompleteDownStream() {
        TestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new TestSubscriber<>();
        multiplexer.changeNotifications().subscribe(testSubscriber);

        // Add two instances
        InstanceController controller1 = new InstanceController(SampleInstanceInfo.DiscoveryServer.build());
        InstanceController controller2 = new InstanceController(SampleInstanceInfo.ZuulServer.build());
        controller1.publishAdd();
        controller2.publishAdd();
        upgradeTo(controller1, controller2);
        assertThat(asSet(notifications.next(), notifications.next()), containsInAnyOrder(controller1.addNotification, controller2.addNotification));
        assertThat(controller1.instanceSubject.hasObservers(), is(true));
        assertThat(controller2.instanceSubject.hasObservers(), is(true));

        multiplexer.unregister();

        testSubscriber.awaitTerminalEvent(5, TimeUnit.SECONDS);
        testSubscriber.assertTerminalEvent();
        testSubscriber.assertNoErrors();
        assertThat(controller1.instanceSubject.hasObservers(), is(false));
        assertThat(controller2.instanceSubject.hasObservers(), is(false));
    }

    private void upgradeTo(InstanceController... controllers) {
        if (controllers.length == 1) {
            multiplexer.update(controllers[0].interest);
        } else {
            Interest<InstanceInfo>[] interests = new Interest[controllers.length];
            for (int i = 0; i < controllers.length; i++) {
                interests[i] = controllers[i].interest;
            }
            multiplexer.update(new MultipleInterests<>(interests));
        }
    }

    class InstanceController {
        InstanceInfo instance;
        Interest<InstanceInfo> interest;
        ReplaySubject<ChangeNotification<InstanceInfo>> instanceSubject;

        // Notifications
        ChangeNotification<InstanceInfo> addNotification;
        ChangeNotification<InstanceInfo> modifyNotification;

        InstanceController(InstanceInfo instance) {
            this.instance = instance;
            interest = Interests.forInstances(instance.getId());
            instanceSubject = ReplaySubject.create();
            addNotification = new ChangeNotification<>(Kind.Add, instance);
            modifyNotification = new ModifyNotification<>(instance, Collections.<Delta<?>>singleton(SampleDelta.StatusUp.build()));

            when(registryMock.forInterest(interest)).thenReturn(instanceSubject);
        }

        void publishAdd() {
            instanceSubject.onNext(addNotification);
        }

        void publishModify() {
            instanceSubject.onNext(modifyNotification);
        }
    }
}
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

package com.netflix.eureka2.server.transport.tcp.discovery;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.InterestRegistration;
import com.netflix.eureka2.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.RxBlocking;
import com.netflix.eureka2.rx.TestableObservableConnection;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class TcpDiscoveryHandlerTest {

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);

    private TestableObservableConnection<Object, Object> observableConnection;
    private TcpDiscoveryHandler handler;

    @Before
    public void setUp() {
        observableConnection = new TestableObservableConnection<>();
        when(registry.forInterest(any(Interest.class))).thenReturn(Observable.<ChangeNotification<InstanceInfo>>empty());
        when(registry.forInterest(any(MultipleInterests.class))).thenReturn(Observable.<ChangeNotification<InstanceInfo>>empty());
        handler = spy(new TcpDiscoveryHandler(registry, EurekaServerMetricFactory.serverMetrics()));
    }

    @Test(timeout = 60000)
    public void testSuccessfulInterestRegistration() {
        Observable<Void> lifecycle = handler.handle(observableConnection);

        Interest<InstanceInfo> interest = Interests.forFullRegistry();
        observableConnection.testableChannelRead().onNext(new InterestRegistration(interest));

        verify(registry, times(1)).forInterest(interest);
    }

    @Test(timeout = 60000)
    public void testSuccessfulUnregisterInterestCloseInternalChannel() {
        Observable<Void> lifecycle = handler.handle(observableConnection);

        observableConnection.testableChannelRead().onNext(new UnregisterInterestSet());
        Assert.assertTrue("Channel shall be closed by now", RxBlocking.isCompleted(1, TimeUnit.SECONDS, lifecycle));

        verify(registry, times(1)).forInterest(Interests.forNone());
    }

    @Test(timeout = 60000)
    public void testSendingNotificationsOnInterestRegistration() {
        Observable<Void> lifecycle = handler.handle(observableConnection);

        ChangeNotification<InstanceInfo> notification = SampleChangeNotification.DiscoveryAdd.newNotification();

        Interest<InstanceInfo> interest = Interests.forFullRegistry();
        when(registry.forInterest(interest)).thenReturn(Observable.just(notification));//.concatWith(Observable.<ChangeNotification<InstanceInfo>>never()));

        observableConnection.testableChannelRead().onNext(new InterestRegistration(interest));

        Iterator updatesIterator = RxBlocking.iteratorFrom(5, TimeUnit.SECONDS, observableConnection.testableChannelWrite());
        Assert.assertEquals(new AddInstance(notification.getData()), updatesIterator.next());
    }
}
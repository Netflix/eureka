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

package com.netflix.eureka2.client.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientInterestChannel;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.client.registry.swap.ThresholdStrategy;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.client.metric.EurekaClientMetricFactory.*;
import static com.netflix.eureka2.client.registry.EurekaClientRegistryProxy.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class EurekaClientRegistryProxyTest {

    private final TestScheduler scheduler = Schedulers.test();

    private final ClientChannelFactory channelFactory = mock(ClientChannelFactory.class);
    private final ClientInterestChannel interestChannel = mock(ClientInterestChannel.class);
    private ReplaySubject<Void> channelLifecycle;

    private final RegistrySwapStrategyFactory swapStrategyFactory = ThresholdStrategy.factoryFor(scheduler);

    private EurekaClientRegistryProxy proxyRegistry;
    private EurekaClientRegistry<InstanceInfo> internalRegistry;

    @Before
    public void setUp() throws Exception {
        when(channelFactory.newInterestChannel(any(EurekaClientRegistry.class))).thenReturn(interestChannel);
        withNewChannelLifecycle();

        proxyRegistry = new EurekaClientRegistryProxy(
                channelFactory, swapStrategyFactory, DEFAULT_INITIAL_DELAY, clientMetrics(), scheduler);

        captureInternalRegistryFromChannel();
    }

    @Test
    public void testProxiesAccessToInternalRegistry() throws Exception {
        addDataToRegistry();

        List<ChangeNotification<InstanceInfo>> notifications = collectForInterest(Interests.forFullRegistry());
        assertThat(notifications.size(), is(equalTo(1)));

        List<InstanceInfo> snapshot = collectSnapshotForInterest(Interests.forFullRegistry());
        assertThat(snapshot.size(), is(equalTo(1)));
    }

    @Test
    public void testCleansUpResources() throws Exception {
        proxyRegistry.shutdown().subscribe();
        verify(channelFactory, times(1)).shutdown();
    }

    @Test
    public void testSwapsRegistriesAfterChannelFailure() throws Exception {
        // First channel connection
        addDataToRegistry();
        List<ChangeNotification<InstanceInfo>> notifications = collectForInterest(Interests.forFullRegistry());
        assertThat(notifications.size(), is(equalTo(1)));

        // Channel failure
        when(interestChannel.change(any(Interest.class))).thenReturn(Observable.<Void>empty());
        channelLifecycle.onError(new Exception("channel error"));

        // Move in time till retry point
        scheduler.advanceTimeBy(DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS);

        // Check automatic interest subscription for the last interest set
        verify(interestChannel).change(new MultipleInterests<InstanceInfo>(Interests.forFullRegistry()));

        // We have new internal registry.
        captureInternalRegistryFromChannel();

        // Verify that we still provide old registry content.
        notifications = collectForInterest(Interests.forFullRegistry());
        assertThat(notifications.size(), is(equalTo(1)));

        // Push new content, which should result in:
        // 1. pending subscriptions termination
        // 2. replacing pre-filled registry with a new one
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        proxyRegistry.forInterest(Interests.forFullRegistry()).doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                errorRef.set(throwable);
            }
        }).subscribe();
        addDataToRegistry();

        assertThat(errorRef.get(), is(instanceOf(Exception.class)));

        notifications = collectForInterest(Interests.forFullRegistry());
        assertThat(notifications.size(), is(equalTo(1)));
    }

    protected void withNewChannelLifecycle() {
        channelLifecycle = ReplaySubject.create();
        when(interestChannel.asLifecycleObservable()).thenReturn(channelLifecycle);
    }

    protected void captureInternalRegistryFromChannel() {
        ArgumentCaptor<EurekaClientRegistry> argCaptor = ArgumentCaptor.forClass(EurekaClientRegistry.class);
        verify(channelFactory, atLeastOnce()).newInterestChannel(argCaptor.capture());

        internalRegistry = argCaptor.getValue();
    }

    private void addDataToRegistry() {
        InstanceInfo info = SampleInstanceInfo.DiscoveryServer.build();
        internalRegistry.register(info);
    }

    private List<ChangeNotification<InstanceInfo>> collectForInterest(Interest<InstanceInfo> interest) {
        when(interestChannel.appendInterest(interest)).thenReturn(Observable.<Void>empty());

        final List<ChangeNotification<InstanceInfo>> items = new ArrayList<>();
        proxyRegistry.forInterest(interest).subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> notification) {
                items.add(notification);
            }
        });

        verify(interestChannel, atLeastOnce()).appendInterest(interest);

        return items;
    }

    private List<InstanceInfo> collectSnapshotForInterest(Interest<InstanceInfo> interest) {
        final List<InstanceInfo> items = new ArrayList<>();
        proxyRegistry.forSnapshot(interest).subscribe(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                items.add(instanceInfo);
            }
        });

        return items;
    }
}
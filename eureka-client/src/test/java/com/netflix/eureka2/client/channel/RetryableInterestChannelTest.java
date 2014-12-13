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

package com.netflix.eureka2.client.channel;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.client.registry.swap.ThresholdStrategy;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.client.channel.RetryableInterestChannel.*;
import static com.netflix.eureka2.client.metric.EurekaClientMetricFactory.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RetryableInterestChannelTest {

    private final TestScheduler scheduler = Schedulers.test();

    private static final Interest<InstanceInfo> INTEREST = Interests.forApplications("testApp");
    private static final InstanceInfo INFO = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp").build();

    private final ClientChannelFactory channelFactory = mock(ClientChannelFactory.class);
    private final ClientInterestChannel interestChannel = mock(ClientInterestChannel.class);
    private ReplaySubject<Void> channelLifecycle;

    private final RegistrySwapStrategyFactory swapStrategyFactory = ThresholdStrategy.factoryFor(scheduler);

    private EurekaClientRegistry<InstanceInfo> firstInternalRegistry;

    private RetryableInterestChannel retryableInterestChannel;

    @Before
    public void setUp() throws Exception {
        when(channelFactory.newInterestChannel(any(EurekaClientRegistry.class))).thenReturn(interestChannel);
        withNewChannelLifecycle();

        retryableInterestChannel = new RetryableInterestChannel(
                channelFactory, swapStrategyFactory, clientMetrics(), DEFAULT_INITIAL_DELAY, scheduler);

        firstInternalRegistry = captureInternalRegistryFromChannel();
    }

    @Test
    public void testDelegatesCallsToInternalChannel() throws Exception {
        // Change operation
        when(interestChannel.change(INTEREST)).thenReturn(Observable.<Void>empty());
        retryableInterestChannel.change(INTEREST).subscribe();
        verify(interestChannel, times(1)).change(INTEREST);

        // Append operation
        when(interestChannel.appendInterest(INTEREST)).thenReturn(Observable.<Void>empty());
        retryableInterestChannel.appendInterest(INTEREST).subscribe();
        verify(interestChannel, times(1)).appendInterest(INTEREST);

        // Append operation
        when(interestChannel.removeInterest(INTEREST)).thenReturn(Observable.<Void>empty());
        retryableInterestChannel.removeInterest(INTEREST).subscribe();
        verify(interestChannel, times(1)).removeInterest(INTEREST);
    }

    @Test
    public void testCleansUpResources() throws Exception {
        retryableInterestChannel.close();
        verify(channelFactory, times(1)).shutdown();
    }

    @Test
    public void testSwapsRegistriesAfterChannelFailure() throws Exception {
        // Make a subscription, and add some data to active registry
        when(interestChannel.appendInterest(INTEREST)).thenReturn(Observable.<Void>empty());
        retryableInterestChannel.appendInterest(INTEREST).subscribe();

        firstInternalRegistry.register(INFO).subscribe();
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(1)));

        // Channel failure; match any interest as there will be immediate automatic resubscription
        when(interestChannel.appendInterest(any(MultipleInterests.class))).thenReturn(Observable.<Void>empty());
        channelLifecycle.onError(new Exception("channel error"));

        // Move in time till retry point
        withNewChannelLifecycle();
        scheduler.advanceTimeBy(DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS);

        // Check automatic interest subscription for the last interest set
        verify(interestChannel).appendInterest(new MultipleInterests<InstanceInfo>(INTEREST));

        // We have new internal registry.
        EurekaClientRegistry<InstanceInfo> secondInternalRegistry = captureInternalRegistryFromChannel();
        assertThat(secondInternalRegistry, is(notNullValue()));

        // Verify that we still provide old registry content.
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(1)));

        // Push new content, which should result in:
        // 1. pending subscriptions termination (shutdown first registry)
        // 2. replacing pre-filled registry with a new one
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        retryableInterestChannel.associatedRegistry().forInterest(INTEREST).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                errorRef.set(e);
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                // Ignore
            }
        });
        secondInternalRegistry.register(INFO).subscribe();

        assertThat(errorRef.get(), is(instanceOf(Exception.class)));
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(1)));
    }

    protected void withNewChannelLifecycle() {
        channelLifecycle = ReplaySubject.create();
        when(interestChannel.asLifecycleObservable()).thenReturn(channelLifecycle);
    }

    protected EurekaClientRegistry<InstanceInfo> captureInternalRegistryFromChannel() {
        ArgumentCaptor<EurekaClientRegistry> argCaptor = ArgumentCaptor.forClass(EurekaClientRegistry.class);
        verify(channelFactory, atLeastOnce()).newInterestChannel(argCaptor.capture());

        return argCaptor.getValue();
    }
}
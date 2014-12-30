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
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.registry.swap.RegistrySwapStrategyFactory;
import com.netflix.eureka2.client.registry.swap.ThresholdStrategy;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.client.metric.EurekaClientMetricFactory.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
public class RetryableInterestChannelTest {

    private final TestScheduler scheduler = Schedulers.test();

    private static final Interest<InstanceInfo> INTEREST = Interests.forApplications("testApp");
    private static final Interest<InstanceInfo> INTEREST2 = Interests.forApplications("testApp2");
    private static final InstanceInfo INFO = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp").build();
    private static final InstanceInfo INFO2 = SampleInstanceInfo.DiscoveryServer.builder().withApp("testApp2").build();

    // we need to have actual delegate channels for close() purposes, and as such, we need to mock the underlying
    // messageConnection and TransportClient
    private final MessageConnection mockConnection = mock(MessageConnection.class);
    private final TransportClient mockClient = mock(TransportClient.class);
    private final ClientChannelFactory channelFactory = mock(ClientChannelFactory.class);
    private final ReplaySubject<Void> channelLifecycle1 = ReplaySubject.create();
    private final ReplaySubject<Void> channelLifecycle2 = ReplaySubject.create();

    // set swap to 40% so tests run
    private final RegistrySwapStrategyFactory swapStrategyFactory = ThresholdStrategy.factoryFor(40, ThresholdStrategy.DEFAULT_RELAX_INTERVAL, scheduler);

    private ClientInterestChannel interestChannel1;
    private ClientInterestChannel interestChannel2; // separate channel obj for the retry test

    private EurekaClientRegistry<InstanceInfo> firstInternalRegistry;
    private EurekaClientRegistry<InstanceInfo> secondInternalRegistry;

    private RetryableInterestChannel retryableInterestChannel;

    @Before
    public void setUp() throws Exception {
        when(mockConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
        when(mockConnection.incoming()).thenReturn(Observable.<Object>just(true));
        when(mockConnection.lifecycleObservable()).thenReturn(ReplaySubject.<Void>create());
        when(mockClient.connect()).thenReturn(Observable.just(mockConnection));

        interestChannel1 = spy(new InterestChannelImpl(mockClient, EurekaClientMetricFactory.clientMetrics()));
        interestChannel2 = spy(new InterestChannelImpl(mockClient, EurekaClientMetricFactory.clientMetrics())); // separate channel obj for the retry test

        when(channelFactory.newInterestChannel())
                .thenReturn(interestChannel1)
                .thenReturn(interestChannel2);

        doReturn(channelLifecycle1).when(interestChannel1).asLifecycleObservable();
        doReturn(channelLifecycle2).when(interestChannel2).asLifecycleObservable();

        firstInternalRegistry = interestChannel1.associatedRegistry();
        secondInternalRegistry = interestChannel2.associatedRegistry();

        retryableInterestChannel = new RetryableInterestChannel(
                channelFactory, swapStrategyFactory, clientMetrics(), RetryableInterestChannel.DEFAULT_INITIAL_DELAY, scheduler);

        when(interestChannel1.associatedRegistry()).thenReturn(firstInternalRegistry);
    }

    @Test
    public void testForwardsRequestsToDelegate() throws Exception {
        // Append operation
        retryableInterestChannel.appendInterest(INTEREST).subscribe();
        verify(interestChannel1, times(1)).appendInterest(INTEREST);

        // Append operation
        retryableInterestChannel.removeInterest(INTEREST).subscribe();
        verify(interestChannel1, times(1)).removeInterest(INTEREST);
    }

    @Test
    public void testCleansUpResources() throws Exception {
        retryableInterestChannel.close();
        verify(channelFactory, times(1)).shutdown();
    }

    @Test
    public void testReconnectWithSwappedRegistriesAfterChannelFailure() throws Exception {
        // Make a subscription, and add some data to active registry
        retryableInterestChannel.appendInterest(INTEREST).toBlocking().firstOrDefault(null);
        retryableInterestChannel.appendInterest(INTEREST2).toBlocking().firstOrDefault(null);

        verify(interestChannel1, timeout(1)).appendInterest(INTEREST);
        verify(interestChannel1, timeout(1)).appendInterest(INTEREST2);

        firstInternalRegistry.register(INFO).subscribe();
        assertThat(retryableInterestChannel.associatedRegistry(), equalTo(firstInternalRegistry));
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(1)));

        firstInternalRegistry.register(INFO2).subscribe();
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(2)));

        // Channel failure; match any interest for channel2 as there will be immediate automatic resubscription
//        when(interestChannel2.appendInterest(any(MultipleInterests.class))).thenReturn(Observable.<Void>empty());
        channelLifecycle1.onError(new Exception("channel error msg"));  // break the channel

        // Move in time till retry point
        scheduler.advanceTimeBy(RetryableInterestChannel.DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS);

        // Check automatic interest subscription for the last interest set
        verify(interestChannel2).appendInterest(new MultipleInterests<>(INTEREST, INTEREST2));

        // We have new internal registry.
        // Verify that we still provide old registry content.
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(2)));

        // Push new content, which should result in:
        // 1. pending subscriptions termination (shutdown first registry which onCompletes)
        // 2. replacing pre-filled registry with a new one
        final AtomicBoolean completed = new AtomicBoolean(false);
        firstInternalRegistry.forInterest(INTEREST).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                // at this point in time, the retryable channel should have the new registry already
                if (retryableInterestChannel.associatedRegistry().equals(secondInternalRegistry)) {
                    completed.set(true);
                }
            }

            @Override
            public void onError(Throwable e) {
                fail("Should not onError");
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                // Ignore
            }
        });
        // push new content on the second registry to simulate server side new notification
        // this should trigger the 40% swap threshold and flip the two registries
        secondInternalRegistry.register(INFO).subscribe();
        verify(interestChannel1, times(1)).close();

        assertThat(completed.get(), is(true));
        assertThat(retryableInterestChannel.associatedRegistry().size(), is(equalTo(1)));
    }
}
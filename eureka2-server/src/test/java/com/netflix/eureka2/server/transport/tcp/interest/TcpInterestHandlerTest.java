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

package com.netflix.eureka2.server.transport.tcp.interest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.interest.InterestRegistration;
import com.netflix.eureka2.protocol.interest.UnregisterInterestSet;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.rx.TestableObservableConnection;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.transport.Acknowledgement;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.ReplaySubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class TcpInterestHandlerTest {

    private final TestScheduler testScheduler = Schedulers.test();

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);

    private final TestableObservableConnection<Object, Object> observableConnection = new TestableObservableConnection<>();
    private final EurekaHealthStatusAggregator systemHealthStatus = mock(EurekaHealthStatusAggregator.class);
    private final EurekaServerConfig config = EurekaServerConfig.baseBuilder().build();

    private final ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();

    private TcpInterestHandler handler;

    @Before
    public void setUp() {
        when(registry.forInterest(any(Interest.class))).thenReturn(Observable.<ChangeNotification<InstanceInfo>>empty());
    }

    @Test
    public void testInterestRegistrationsAreDiscardedIfServerNotUp() throws Exception {
        when(systemHealthStatus.healthStatus()).thenReturn(Observable.<HealthStatusUpdate<EurekaHealthStatusAggregator>>never());
        createHandlerAndConnect();

        handler.handle(observableConnection).subscribe(testSubscriber);
        testSubscriber.assertOnError();
    }

    @Test
    public void testRetryOnHealthStatusStream() throws Exception {
        final AtomicInteger subscribeRound = new AtomicInteger();
        when(systemHealthStatus.healthStatus()).thenReturn(Observable.create(new OnSubscribe<HealthStatusUpdate<EurekaHealthStatusAggregator>>() {
            @Override
            public void call(Subscriber<? super HealthStatusUpdate<EurekaHealthStatusAggregator>> subscriber) {
                if (subscribeRound.getAndIncrement() == 0) {
                    subscriber.onError(new Exception("error"));
                }
                subscriber.onNext(new HealthStatusUpdate<EurekaHealthStatusAggregator>(Status.UP, null));
                subscriber.onCompleted();
            }
        }));

        // Create handler and advance time to we run through a retry cycle on health check.
        handler = new TcpInterestHandler(config, registry, systemHealthStatus, EurekaServerMetricFactory.serverMetrics(), testScheduler);
        testScheduler.advanceTimeBy(TcpInterestHandler.RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);

        handler.handle(observableConnection).subscribe(testSubscriber);
        testSubscriber.assertOpen();
    }

    @Test(timeout = 60000)
    public void testSuccessfulInterestRegistration() {
        emitHealthStatusUpCreateHandlerAndConnect();

        Interest<InstanceInfo> interest = Interests.forFullRegistry();
        observableConnection.testableChannelRead().onNext(new InterestRegistration(interest));

        verify(registry, times(1)).forInterest(interest);
    }

    @Test(timeout = 60000)
    public void testSuccessfulUnregisterInterestCloseInternalChannel() throws Exception {
        emitHealthStatusUpCreateHandlerAndConnect();

        observableConnection.testableChannelRead().onNext(new UnregisterInterestSet());
        testSubscriber.assertOnCompleted(1, TimeUnit.SECONDS);

        verify(registry, times(1)).forInterest(Interests.forNone());
    }

    @Test(timeout = 60000)
    public void testSendingNotificationsOnInterestRegistration() {
        emitHealthStatusUpCreateHandlerAndConnect();

        ChangeNotification<InstanceInfo> notification = SampleChangeNotification.DiscoveryAdd.newNotification();

        Interest<InstanceInfo> interest = Interests.forFullRegistry();
        when(registry.forInterest(interest)).thenReturn(Observable.just(notification));

        observableConnection.testableChannelRead().onNext(new InterestRegistration(interest));
        ExtTestSubscriber<Object> outputSubscriber = new ExtTestSubscriber<>();
        observableConnection.testableChannelWrite().subscribe(outputSubscriber);

        Object expected = new AddInstance(notification.getData());
        assertThat(outputSubscriber.takeNextOrFail(), is(equalTo((Object) Acknowledgement.INSTANCE)));
        assertThat(outputSubscriber.takeNextOrFail(), is(equalTo(expected)));
    }

    private void emitHealthStatusUpCreateHandlerAndConnect() {
        ReplaySubject<HealthStatusUpdate<EurekaHealthStatusAggregator>> healthStatusSubject = ReplaySubject.create();
        healthStatusSubject.onNext(new HealthStatusUpdate<EurekaHealthStatusAggregator>(Status.UP, null));

        when(systemHealthStatus.healthStatus()).thenReturn(healthStatusSubject);

        createHandlerAndConnect();

        testSubscriber.assertOpen();
    }

    private void createHandlerAndConnect() {
        handler = new TcpInterestHandler(config, registry, systemHealthStatus, EurekaServerMetricFactory.serverMetrics(), testScheduler);
        testScheduler.triggerActions();
        handler.handle(observableConnection).subscribe(testSubscriber);
    }
}
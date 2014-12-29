package com.netflix.eureka2.client;

import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.ClientChannelFactoryImpl;
import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.InterestHandler;
import com.netflix.eureka2.client.registry.InterestHandlerImpl;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.ReplaySubject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class EurekaClientTest {

    private final MessageConnection mockConnection = mock(MessageConnection.class);
    private final TransportClient mockReadTransportClient = mock(TransportClient.class);
    private final TransportClient mockWriteTransportClient = mock(TransportClient.class);

    protected EurekaClient client;
    protected PreservableEurekaRegistry registry;
    protected List<ChangeNotification<InstanceInfo>> allRegistry;
    protected List<ChangeNotification<InstanceInfo>> discoveryRegistry;
    protected List<ChangeNotification<InstanceInfo>> zuulRegistry;
    protected Interest<InstanceInfo> interestAll;
    protected Interest<InstanceInfo> interestDiscovery;
    protected Interest<InstanceInfo> interestZuul;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            interestAll = Interests.forFullRegistry();
            interestDiscovery = Interests.forVips(SampleInstanceInfo.DiscoveryServer.build().getVipAddress());
            interestZuul = Interests.forVips(SampleInstanceInfo.ZuulServer.build().getVipAddress());

            discoveryRegistry = Arrays.asList(
                    SampleChangeNotification.DiscoveryAdd.newNotification(),
                    SampleChangeNotification.DiscoveryAdd.newNotification()
            );
            zuulRegistry = Arrays.asList(
                    SampleChangeNotification.ZuulAdd.newNotification(),
                    SampleChangeNotification.ZuulAdd.newNotification()
            );
            allRegistry = new ArrayList<>(discoveryRegistry);
            allRegistry.addAll(zuulRegistry);

            registry = new PreservableEurekaRegistry(
                    new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics()),
                    new BasicEurekaRegistryConfig(),
                    EurekaRegistryMetricFactory.registryMetrics());
            for (ChangeNotification<InstanceInfo> notification : allRegistry) {
                registry.register(notification.getData()).toBlocking().firstOrDefault(null);
            }

            when(mockConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
            when(mockConnection.incoming()).thenReturn(Observable.never());
            when(mockConnection.lifecycleObservable()).thenReturn(ReplaySubject.<Void>create());
            when(mockReadTransportClient.connect()).thenReturn(Observable.just(mockConnection));

            ClientChannelFactory clientChannelFactory = new ClientChannelFactoryImpl(
                    mockWriteTransportClient,
                    mockReadTransportClient,
                    registry,
                    1000,
                    EurekaClientMetricFactory.clientMetrics()
            );

            InterestHandler interestHandler = new InterestHandlerImpl(registry, clientChannelFactory);

            client = new EurekaClientImpl(interestHandler, null);
        }

        @Override
        protected void after() {

            // Sleep for a bit before closing the client. We need to do this as the appendInterest on the channel is
            // async to the forInterest from the registry, so it's possible that we arrive here (i.e. with all 4 change
            // notifications expected) before the appendInterest is called on the InterestChannelImpl. If we close the
            // client too early then the stream will onError instead as we will be calling appendInterest on a closed
            // channel. We don't want this for the test as we want to be more deterministic, but in real usage this
            // is acceptable.
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}

            client.close();
        }
    };


    // =======================
    // registration path tests
    // =======================

    // TODO


    // =======================
    // interest path tests
    // =======================

    @Test
    public void testForInterestSingleUser() throws Exception {
        final List<ChangeNotification<InstanceInfo>> output = new ArrayList<>();

        final CountDownLatch onNextLatch = new CountDownLatch(4);
        final CountDownLatch onCompletedLatch = new CountDownLatch(1);
        Subscription interestSubscription = client.forInterest(interestAll).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                onCompletedLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                output.add(notification);
                onNextLatch.countDown();
            }
        });

        assertThat(onNextLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        // Sleep for a bit before closing the client. We need to do this as the appendInterest on the channel is
        // async to the forInterest from the registry, so it's possible that we arrive here (i.e. with all 4 change
        // notifications expected) before the appendInterest is called on the InterestChannelImpl. If we close the
        // client too early then the stream will onError instead as we will be calling appendInterest on a closed
        // channel. We don't want this for the test as we want to be more deterministic, but in real usage this
        // is acceptable.
        Thread.sleep(500);

        client.close();
        assertThat(onCompletedLatch.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(interestSubscription.isUnsubscribed(), equalTo(true));
        assertThat(output, containsInAnyOrder(allRegistry.toArray()));
    }

    @Test
    public void testForInterestSameTwoUsers() throws Exception {
        final List<ChangeNotification<InstanceInfo>> output1 = new ArrayList<>();

        final CountDownLatch completionLatch = new CountDownLatch(2);

        final CountDownLatch latch1 = new CountDownLatch(4);
        client.forInterest(interestAll).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("should not onError");
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                output1.add(notification);
                latch1.countDown();
            }
        });

        final List<ChangeNotification<InstanceInfo>> output2 = new ArrayList<>();

        final CountDownLatch latch2 = new CountDownLatch(4);
        client.forInterest(interestAll).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("should not onError");
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                output2.add(notification);
                latch2.countDown();
            }
        });

        assertThat(latch1.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(latch1.await(2, TimeUnit.MINUTES), equalTo(true));

        assertThat(output1, containsInAnyOrder(allRegistry.toArray()));
        assertThat(output2, containsInAnyOrder(allRegistry.toArray()));
    }

    @Test
    public void testForInterestDifferentTwoUsers() throws Exception {
        final List<ChangeNotification<InstanceInfo>> discoveryOutput = new ArrayList<>();

        final CountDownLatch completionLatch = new CountDownLatch(2);

        final CountDownLatch latch1 = new CountDownLatch(2);
        client.forInterest(interestDiscovery).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("should not onError");
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                discoveryOutput.add(notification);
                latch1.countDown();
            }
        });

        final List<ChangeNotification<InstanceInfo>> zuulOutput = new ArrayList<>();

        final CountDownLatch latch2 = new CountDownLatch(2);
        client.forInterest(interestZuul).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("should not onError");
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                zuulOutput.add(notification);
                latch2.countDown();
            }
        });

        assertThat(latch1.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(latch2.await(1, TimeUnit.MINUTES), equalTo(true));

        assertThat(discoveryOutput, containsInAnyOrder(discoveryRegistry.toArray()));
        assertThat(zuulOutput, containsInAnyOrder(zuulRegistry.toArray()));
    }

    @Test
    public void testForInterestSecondInterestSupercedeFirst() throws Exception {
        final List<ChangeNotification<InstanceInfo>> discoveryOutput = new ArrayList<>();

        final CountDownLatch completionLatch = new CountDownLatch(2);

        final CountDownLatch latch1 = new CountDownLatch(2);
        client.forInterest(interestDiscovery)
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Assert.fail("should not onError");
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        discoveryOutput.add(notification);
                        latch1.countDown();
                    }
                });

        // don't use all registry interest as it is a special singleton
        Interest<InstanceInfo> compositeInterest = new MultipleInterests<>(interestDiscovery, interestZuul);

        final List<ChangeNotification<InstanceInfo>> compositeOutput = new ArrayList<>();

        final CountDownLatch latch2 = new CountDownLatch(2);
        client.forInterest(compositeInterest)
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        completionLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Assert.fail("should not onError");
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        compositeOutput.add(notification);
                        latch2.countDown();
                    }
                });

        assertThat(latch1.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(latch1.await(2, TimeUnit.MINUTES), equalTo(true));

        assertThat(discoveryOutput, containsInAnyOrder(discoveryRegistry.toArray()));

        List<ChangeNotification<InstanceInfo>> compositeRegistry = new ArrayList<>(discoveryRegistry);
        compositeRegistry.addAll(zuulRegistry);
        assertThat(compositeOutput, containsInAnyOrder(compositeRegistry.toArray()));
    }

    @Test
    public void testSoleUserUnsubscribeCancelChannelSubscription() {

    }

    @Test
    public void testOneUserUnsubscribeRetainChannelSubscription() {

    }

    @Test
    public void testCloseClientCompleteAllSubscribedUsers() {

    }
}

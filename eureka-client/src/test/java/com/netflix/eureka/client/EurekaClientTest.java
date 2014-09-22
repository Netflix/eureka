package com.netflix.eureka.client;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.interests.MultipleInterests;
import com.netflix.eureka.interests.SampleChangeNotification;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.EurekaRegistryImpl;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class EurekaClientTest {

    @Mock
    protected InterestChannel interestChannel;

    protected EurekaClient client;
    protected EurekaRegistry<InstanceInfo> registry;
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
            interestDiscovery = Interests.forVip(SampleInstanceInfo.DiscoveryServer.build().getVipAddress());
            interestZuul = Interests.forVip(SampleInstanceInfo.ZuulServer.build().getVipAddress());

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

            registry = new EurekaRegistryImpl();
            for (ChangeNotification<InstanceInfo> notification : allRegistry) {
                registry.register(notification.getData());
            }

            // interest channel mocks
            when(interestChannel.change(org.mockito.Mockito.any(Interest.class))).thenReturn(Observable.empty().cast(Void.class));

            client = new EurekaClientImpl(registry, null);
        }

        @Override
        protected void after() {
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

        final CountDownLatch latch = new CountDownLatch(5);
        client.forInterest(interestAll).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("should not onError");
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                output.add(notification);
                latch.countDown();
                if (latch.getCount() == 1) {
                    client.close();  // this sends an onComplete to the stream
                }
            }
        });

        assertThat(latch.await(1, TimeUnit.MINUTES), equalTo(true));

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

package com.netflix.eureka.client;

import com.netflix.eureka.client.service.EurekaClientService;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.interests.SampleChangeNotification;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
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

    @Mock
    protected EurekaClientService eurekaService;

    protected EurekaClient client;
    protected EurekaRegistry<InstanceInfo> registry;
    protected InterestProcessor processor;
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

            Observable<ChangeNotification<InstanceInfo>> mockInterestStream = Observable.from(allRegistry);
            registry = new LeasedInstanceRegistry(null);
            for (ChangeNotification<InstanceInfo> notification : allRegistry) {
                registry.register(notification.getData());
            }

            when(interestChannel.register(eq(interestAll))).thenReturn(mockInterestStream);
            when(interestChannel.register(eq(interestDiscovery))).thenReturn(mockInterestStream.filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<InstanceInfo> notification) {
                    return interestDiscovery.matches(notification.getData());
                }
            }));
            when(interestChannel.register(eq(interestZuul))).thenReturn(mockInterestStream.filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<InstanceInfo> notification) {
                    return interestZuul.matches(notification.getData());
                }
            }));
            when(interestChannel.upgrade(eq(interestAll))).thenReturn(Observable.<Void>empty());
            when(interestChannel.upgrade(eq(interestDiscovery))).thenReturn(Observable.<Void>empty());
            when(interestChannel.upgrade(eq(interestZuul))).thenReturn(Observable.<Void>empty());

            when(eurekaService.newInterestChannel()).thenReturn(interestChannel);
            when(eurekaService.forInterest(eq(interestAll))).thenReturn(registry.forInterest(interestAll));
            when(eurekaService.forInterest(eq(interestDiscovery))).thenReturn(registry.forInterest(interestDiscovery));
            when(eurekaService.forInterest(eq(interestZuul))).thenReturn(registry.forInterest(interestZuul));

            processor = new InterestProcessor(interestChannel);

            client = new EurekaClientImpl(eurekaService);
        }

        @Override
        protected void after() {
            registry.shutdown();
            processor.shutdown();
        }
    };




    @Test
    public void testCloseClient() {

    }

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
                    registry.shutdown();  // this sends an onComplete to the stream
                }
            }
        });

        assertThat(latch.await(1, TimeUnit.MINUTES), equalTo(true));

        assertThat(output, containsInAnyOrder(allRegistry.toArray()));
    }

    @Test
    public void testForInterestSameTwoUsers() throws Exception {
        final List<ChangeNotification<InstanceInfo>> output1 = new ArrayList<>();

        final CountDownLatch latch1 = new CountDownLatch(4);
        client.forInterest(interestAll).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                Assert.fail("the stream doesn't onComplete until registry is shutdown");
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
                Assert.fail("the stream doesn't onComplete until registry is shutdown");
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

        final CountDownLatch latch1 = new CountDownLatch(2);
        client.forInterest(interestDiscovery).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                Assert.fail("the stream doesn't onComplete until registry is shutdown");
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
                Assert.fail("the stream doesn't onComplete until registry is shutdown");
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
        assertThat(latch1.await(1, TimeUnit.MINUTES), equalTo(true));

        assertThat(discoveryOutput, containsInAnyOrder(discoveryRegistry.toArray()));
        assertThat(zuulOutput, containsInAnyOrder(zuulRegistry.toArray()));
    }

    @Test
    public void testForInterestSecondInterestSupercedeFirst() {

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

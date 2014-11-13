package com.netflix.eureka2.client.service;

import com.netflix.eureka2.client.registry.EurekaClientRegistry;
import com.netflix.eureka2.client.registry.EurekaClientRegistryImpl;
import com.netflix.eureka2.client.transport.TransportClient;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.SampleInterest;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.SampleAddInstance;
import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.client.metric.EurekaClientMetricFactory.clientMetrics;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class InterestChannelImplTest {

    @Mock
    protected MessageConnection serverConnection;

    @Mock
    protected TransportClient transportClient;

    protected EurekaClientRegistry<InstanceInfo> registry;
    protected InterestChannelImpl channel;

    protected Interest<InstanceInfo> sampleInterestZuul;
    protected Observable<AddInstance> sampleAddMessagesZuul;

    protected Interest<InstanceInfo> sampleInterestDiscovery;
    protected Observable<AddInstance> sampleAddMessagesDiscovery;

    protected Interest<InstanceInfo> sampleInterestAll;
    protected Observable<AddInstance> sampleAddMessagesAll;

    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new EurekaClientRegistryImpl(clientMetrics().getRegistryMetrics());
            when(serverConnection.acknowledge()).thenReturn(Observable.<Void>empty());
            when(serverConnection.submitWithAck(Mockito.anyObject())).thenReturn(Observable.<Void>empty());
            when(transportClient.connect()).thenReturn(Observable.just(serverConnection));

            channel = new InterestChannelImpl(registry, transportClient, clientMetrics().getInterestChannelMetrics());

            sampleInterestZuul = SampleInterest.ZuulApp.build();
            sampleAddMessagesZuul = SampleAddInstance.newMessages(SampleAddInstance.ZuulAdd, 10);

            sampleInterestDiscovery = SampleInterest.DiscoveryApp.build();
            sampleAddMessagesDiscovery = SampleAddInstance.newMessages(SampleAddInstance.DiscoveryAdd, 10);

            sampleInterestAll = Interests.forFullRegistry();
            sampleAddMessagesAll = sampleAddMessagesZuul.concatWith(sampleAddMessagesDiscovery);
        }

        @Override
        protected void after() {
            channel.close();
            registry.shutdown();
        }
    };

    @Test
    public void testChangeWithNewInterest() throws Exception {
        final CountDownLatch streamEndLatch = new CountDownLatch(1);
        when(serverConnection.incoming())
                .thenReturn(sampleAddMessagesZuul.cast(Object.class).doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        streamEndLatch.countDown();
                    }
                }));

        final CountDownLatch completionLatch = new CountDownLatch(1);
        channel.change(sampleInterestZuul).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(completionLatch.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(streamEndLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        Collection<InstanceInfo> actual = registry.forSnapshot(sampleInterestZuul).toList().toBlocking().first();

        Collection<InstanceInfo> expected = sampleAddMessagesZuul
                .map(new Func1<AddInstance, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(AddInstance addInstance) {
                        return addInstance.getInstanceInfo();
                    }
                }).toList().toBlocking().first();

        assertThat(actual, containsInAnyOrder(expected.toArray()));
    }

    @Test
    public void testChangeWithNonIntersectingInterest() throws Exception {
        final CountDownLatch streamEndLatch = new CountDownLatch(1);
        when(serverConnection.incoming())
                .thenReturn(sampleAddMessagesAll.cast(Object.class).doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        streamEndLatch.countDown();
                    }
                }));

        final CountDownLatch completionLatch1 = new CountDownLatch(1);
        channel.change(sampleInterestZuul).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch1.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        final CountDownLatch completionLatch2 = new CountDownLatch(1);
        channel.change(sampleInterestDiscovery).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch2.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(completionLatch1.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(completionLatch2.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(streamEndLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        Collection<InstanceInfo> actual = registry.forSnapshot(sampleInterestZuul).toList().toBlocking().first();
        actual.addAll(registry.forSnapshot(sampleInterestDiscovery).toList().toBlocking().first());

        Collection<InstanceInfo> expected = sampleAddMessagesAll
                .map(new Func1<AddInstance, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(AddInstance addInstance) {
                        return addInstance.getInstanceInfo();
                    }
                }).toList().toBlocking().first();

        assertThat(actual, containsInAnyOrder(expected.toArray()));
    }

    @Test
    public void testChangeWithIntersectingInterest() throws Exception {
        final CountDownLatch streamEndLatch = new CountDownLatch(1);
        when(serverConnection.incoming())
                .thenReturn(sampleAddMessagesAll.cast(Object.class).doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        streamEndLatch.countDown();
                    }
                }));

        final CountDownLatch completionLatch1 = new CountDownLatch(1);
        channel.change(sampleInterestZuul).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch1.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        final CountDownLatch completionLatch2 = new CountDownLatch(1);
        channel.change(new MultipleInterests<>(sampleInterestZuul, sampleInterestDiscovery)).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch2.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(completionLatch1.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(completionLatch2.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(streamEndLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        // set to clear all dupes
        Set<InstanceInfo> actual = new HashSet<>(registry.forSnapshot(sampleInterestZuul).toList().toBlocking().first());
        actual.addAll(registry.forSnapshot(new MultipleInterests<>(sampleInterestZuul, sampleInterestDiscovery)).toList().toBlocking().first());

        Collection<InstanceInfo> expected = sampleAddMessagesAll
                .map(new Func1<AddInstance, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(AddInstance addInstance) {
                        return addInstance.getInstanceInfo();
                    }
                }).toList().toBlocking().first();

        assertThat(actual, containsInAnyOrder(expected.toArray()));
    }

    @Test
    public void testTransportAddWithLowerVersion() throws Exception {
        // preload the channel cache and registry with data
        InstanceInfo original1 = SampleInstanceInfo.DiscoveryServer.builder().withVersion(100l).build();
        InstanceInfo original2 = SampleInstanceInfo.ZuulServer.builder().withVersion(1l).build();
        AddInstance message1 = new AddInstance(original1);
        AddInstance message2 = new AddInstance(original2);

        // new messages with discovery instance with lower version, zuul instance with newer version
        InstanceInfo new1 = new InstanceInfo.Builder().withInstanceInfo(original1).withVersion(10l).build();
        InstanceInfo new2 = new InstanceInfo.Builder().withInstanceInfo(original2).withVersion(10l).build();
        AddInstance message3 = new AddInstance(new1);
        AddInstance message4 = new AddInstance(new2);

        final CountDownLatch streamEndLatch = new CountDownLatch(1);
        when(serverConnection.incoming())
                .thenReturn(Observable.from(Arrays.asList(message1, message2, message3, message4))
                        .cast(Object.class).doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                streamEndLatch.countDown();
                            }
                        }));

        final CountDownLatch completionLatch = new CountDownLatch(1);
        channel.change(sampleInterestAll).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(completionLatch.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(streamEndLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        Collection<InstanceInfo> actual = registry.forSnapshot(sampleInterestAll).toList().toBlocking().first();
        assertThat(actual.size(), equalTo(2));
        for (InstanceInfo instanceInfo : actual) {
            if (instanceInfo.getApp().equals(original1.getApp())) {
                assertThat(instanceInfo, equalTo(original1));
            } else if (instanceInfo.getApp().equals(original2.getApp())) {
                assertThat(instanceInfo, equalTo(new2));
            } else {
                Assert.fail("Should never get here");
            }
        }
    }

    @Test
    public void testTransportUpdateWithLowerVersion() throws Exception {
        // preload the channel cache and registry with data
        InstanceInfo original1 = SampleInstanceInfo.DiscoveryServer.builder().withVersion(100l).build();
        InstanceInfo original2 = SampleInstanceInfo.ZuulServer.builder().withVersion(1l).build();
        AddInstance message1 = new AddInstance(original1);
        AddInstance message2 = new AddInstance(original2);

        // new messages with discovery instance with lower version, zuul instance with newer version
        InstanceInfo new1 = new InstanceInfo.Builder().withInstanceInfo(original1).withVersion(10l).withAsg("foo").build();
        InstanceInfo new2 = new InstanceInfo.Builder().withInstanceInfo(original2).withVersion(10l).withAsg("bar").build();
        Set<Delta<?>> delta1 = new1.diffOlder(original1);
        Set<Delta<?>> delta2 = new2.diffOlder(original2);
        assertThat(delta1.size(), equalTo(1));
        assertThat(delta2.size(), equalTo(1));
        UpdateInstanceInfo<?> message3 = new UpdateInstanceInfo<>(delta1.iterator().next());
        UpdateInstanceInfo<?> message4 = new UpdateInstanceInfo<>(delta2.iterator().next());

        final CountDownLatch streamEndLatch = new CountDownLatch(1);
        when(serverConnection.incoming())
                .thenReturn(Observable.from(Arrays.asList(message1, message2, message3, message4))
                        .cast(Object.class).doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                streamEndLatch.countDown();
                            }
                        }));

        final CountDownLatch completionLatch = new CountDownLatch(1);
        channel.change(sampleInterestAll).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(completionLatch.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(streamEndLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        Collection<InstanceInfo> actual = registry.forSnapshot(sampleInterestAll).toList().toBlocking().first();
        assertThat(actual.size(), equalTo(2));
        for (InstanceInfo instanceInfo : actual) {
            if (instanceInfo.getApp().equals(original1.getApp())) {
                assertThat(instanceInfo, equalTo(original1));
            } else if (instanceInfo.getApp().equals(original2.getApp())) {
                assertThat(instanceInfo, equalTo(new2));
            } else {
                Assert.fail("Should never get here");
            }
        }
    }

    @Test
    public void testTransportDelete() throws Exception {
        // preload the channel cache and registry with data
        InstanceInfo original1 = SampleInstanceInfo.DiscoveryServer.builder().withVersion(100l).build();
        InstanceInfo original2 = SampleInstanceInfo.ZuulServer.builder().withVersion(1l).build();
        AddInstance message1 = new AddInstance(original1);
        AddInstance message2 = new AddInstance(original2);

        DeleteInstance message3 = new DeleteInstance(original1.getId());

        final CountDownLatch streamEndLatch = new CountDownLatch(1);
        when(serverConnection.incoming())
                .thenReturn(Observable.from(Arrays.asList(message1, message2, message3))
                        .cast(Object.class).doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                streamEndLatch.countDown();
                            }
                        }));

        final CountDownLatch completionLatch = new CountDownLatch(1);
        channel.change(sampleInterestAll).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                Assert.fail("Should not onError here");
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(completionLatch.await(1, TimeUnit.MINUTES), equalTo(true));
        assertThat(streamEndLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        Collection<InstanceInfo> actual = registry.forSnapshot(sampleInterestAll).toList().toBlocking().first();
        assertThat(actual.size(), equalTo(1));
        InstanceInfo instanceInfo = actual.iterator().next();
        assertThat(instanceInfo, equalTo(original2));
    }
}

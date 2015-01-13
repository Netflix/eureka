package com.netflix.eureka2.client.channel;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.transport.TransportClient;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.SampleAddInstance;
import com.netflix.eureka2.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
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
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.client.metric.EurekaClientMetricFactory.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class InterestChannelImplTest {

    @Mock
    protected MessageConnection serverConnection;

    @Mock
    protected TransportClient transportClient;

    protected SourcedEurekaRegistry<InstanceInfo> registry;
    protected InterestChannelImpl channel;

    protected Interest<InstanceInfo> sampleInterestZuul;
    protected Observable<AddInstance> sampleAddMessagesZuul;

    protected Interest<InstanceInfo> sampleInterestDiscovery;
    protected Observable<AddInstance> sampleAddMessagesDiscovery;

    protected Interest<InstanceInfo> sampleInterestAll;
    protected Observable<AddInstance> sampleAddMessagesAll;

    private final ReplaySubject<Void> serverConnectionLifecycle = ReplaySubject.create();
    @Rule
    public final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics());
            when(serverConnection.acknowledge()).thenReturn(Observable.<Void>empty());
            when(serverConnection.submitWithAck(Mockito.anyObject())).thenReturn(Observable.<Void>empty());
            when(serverConnection.lifecycleObservable()).thenReturn(serverConnectionLifecycle);
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
    public void testCleanUpResourcesOnClose() throws Exception {
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

        channel.close();
        final CountDownLatch errorLatch = new CountDownLatch(1);
        channel.change(sampleInterestAll).subscribe(new Subscriber<Void>() {  // change after close should onError
            @Override
            public void onCompleted() {
                Assert.fail("should not onComplete here");
            }

            @Override
            public void onError(Throwable e) {
                errorLatch.countDown();
            }

            @Override
            public void onNext(Void aVoid) {
                Assert.fail("Should onNext here");
            }
        });

        assertThat(errorLatch.await(1, TimeUnit.MINUTES), equalTo(true));
    }

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
    public void testTransportDelete() throws Exception {
        // preload the channel cache and registry with data
        InstanceInfo original1 = SampleInstanceInfo.DiscoveryServer.builder().build();
        InstanceInfo original2 = SampleInstanceInfo.ZuulServer.builder().build();
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

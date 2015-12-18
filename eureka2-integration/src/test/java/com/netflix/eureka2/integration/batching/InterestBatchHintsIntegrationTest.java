package com.netflix.eureka2.integration.batching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.spi.protocol.common.InterestSetNotification;
import com.netflix.eureka2.spi.protocol.interest.SampleAddInstance;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class InterestBatchHintsIntegrationTest extends AbstractBatchHintsIntegrationTest {

    private ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber;

    private ReplaySubject<Object> incomingSubject;
    private ReplaySubject<Void> serverConnectionLifecycle;
    private EurekaConnection serverConnection;
    private TransportClient transport;

    private ClientChannelFactory<InterestChannel> channelFactory;
    private List<InterestChannel> createdInterestChannels;

    private EurekaRegistry<InstanceInfo> registry;
    private EurekaInterestClient interestClient;

    @Before
    public void setUp() {
        testSubscriber = new ExtTestSubscriber<>();
        registry = spy(new EurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics()));

        incomingSubject = ReplaySubject.create();
        serverConnectionLifecycle = ReplaySubject.create();

        serverConnection = mock(EurekaConnection.class);
        when(serverConnection.incoming()).thenReturn(incomingSubject);
        when(serverConnection.acknowledge()).thenReturn(Observable.<Void>empty());
        when(serverConnection.lifecycleObservable()).thenReturn(serverConnectionLifecycle);

        transport = mock(TransportClient.class);

        // create a real factory to generate real channels, but use a mock channel and doAnswer on the mock
        // so that we can capture the actual channels returned by the .newChannel() calls.
        final InterestChannelFactory realChannelFactory = new InterestChannelFactory(
                transport,
                registry,
                EurekaClientMetricFactory.clientMetrics()
        );

        channelFactory = mock(InterestChannelFactory.class);

        createdInterestChannels = new ArrayList<>();
        when(channelFactory.newChannel()).thenAnswer(new Answer<InterestChannel>() {
            @Override
            public InterestChannel answer(InvocationOnMock invocation) throws Throwable {
                InterestChannel channel = realChannelFactory.newChannel();
                createdInterestChannels.add(channel);
                return channel;
            }
        });
    }

    @After
    public void tearDown() {
        if (interestClient != null) {
            interestClient.shutdown();
        }
    }

    @Test
    public void testChannelBatchHintsConvertToBufferSentinelAfterData() throws Exception {
        final Interest<InstanceInfo> interest = Interests.forFullRegistry();

        final List<InterestSetNotification> data1 = Arrays.asList(
                newBufferStart(interest),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                newBufferEnd(interest)
        );

        final Observable<InterestSetNotification> remoteData = Observable.timer(100, TimeUnit.MILLISECONDS).
                flatMap(new Func1<Long, Observable<InterestSetNotification>>() {
                    @Override
                    public Observable<InterestSetNotification> call(Long aLong) {
                        return Observable.from(data1);
                    }
                });

        when(transport.connect()).thenReturn(Observable.just(serverConnection));

        when(serverConnection.submitWithAck(Mockito.anyObject())).then(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                remoteData.subscribe(incomingSubject);
                return Observable.empty();
            }
        });

        interestClient = new EurekaInterestClientImpl(registry, channelFactory);  // only create after all mocks
        Observable<ChangeNotification<InstanceInfo>> notifications = interestClient.forInterest(interest);

        notifications.subscribe(testSubscriber);

        testSubscriber.assertProducesInAnyOrder(toChangeNotifications(data1), new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                return notification;
            }
        }, 5000, TimeUnit.MILLISECONDS);

        List<ChangeNotification<InstanceInfo>> received = testSubscriber.getOnNextItems();
        for (int i = 0; i < received.size() - 1; i++) {
            assertThat(received.get(i).getKind(), is(not(ChangeNotification.Kind.BufferSentinel)));
        }
        assertThat(received.get(received.size() - 1).getKind(), is(ChangeNotification.Kind.BufferSentinel));
    }

    @Test
    public void testInterestChannelChangeEvictionOnBufferHints() throws Exception {
        // set up the new connection for the second iteration of the interest channel
        ReplaySubject<Void> serverConnection2Lifecycle = ReplaySubject.create();
        ReplaySubject<Object> incomingSubject2 = ReplaySubject.create();

        EurekaConnection serverConnection2 = mock(EurekaConnection.class);
        when(serverConnection2.incoming()).thenReturn(incomingSubject2);
        when(serverConnection2.acknowledge()).thenReturn(Observable.<Void>empty());
        when(serverConnection2.lifecycleObservable()).thenReturn(serverConnection2Lifecycle);

        when(transport.connect())
                .thenReturn(Observable.just(serverConnection))
                .thenReturn(Observable.just(serverConnection2))
                .thenReturn(null);

        final Interest<InstanceInfo> interest = Interests.forFullRegistry();

        final List<InterestSetNotification> data1 = Arrays.asList(
                newBufferStart(interest),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                SampleAddInstance.ZuulAdd.newMessage(),
                newBufferEnd(interest)
        );

        final Observable<InterestSetNotification> remoteData = Observable.from(data1);

        when(serverConnection.submitWithAck(Mockito.anyObject())).then(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                remoteData.subscribe(incomingSubject);
                return Observable.empty();
            }
        });

        when(serverConnection2.submitWithAck(Mockito.anyObject())).then(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                // don't send any data on the incomingSubject2 yet
                return Observable.empty();
            }
        });

        interestClient = new EurekaInterestClientImpl(registry, channelFactory);  // only create after all mocks
        Observable<ChangeNotification<InstanceInfo>> notifications = interestClient.forInterest(interest);

        notifications.subscribe(testSubscriber);

        List<ChangeNotification<InstanceInfo>> received = testSubscriber.takeNext(8, 5000, TimeUnit.MILLISECONDS);
        for (int i = 0; i < received.size() - 1; i++) {
            assertThat(received.get(i).getKind(), is(not(ChangeNotification.Kind.BufferSentinel)));
        }
        assertThat(received.get(received.size() - 1).getKind(), is(ChangeNotification.Kind.BufferSentinel));

        assertThat(createdInterestChannels.size(), is(1));
        assertThat(createdInterestChannels.get(0), instanceOf(Sourced.class));
        Source firstSource = (createdInterestChannels.get(0)).getSource();
        verifyRegistryContentContainOnlySource(registry, firstSource);

        serverConnectionLifecycle.onError(new Exception("test channel failure"));

        Thread.sleep(200); // give it a bit of time
        verify(registry, times(1)).evictAll(Matchers.any(Source.SourceMatcher.class));  // channel1's eviction event

        incomingSubject2.onNext(newBufferStart(interest));
        Thread.sleep(200); // give it a bit of time
        verify(registry, times(1)).evictAll(Matchers.any(Source.SourceMatcher.class));  // still channel1's event

        incomingSubject2.onNext(newBufferEnd(interest));
        Thread.sleep(2000); // give it a bit of time
        verify(registry, times(2)).evictAll(Matchers.any(Source.SourceMatcher.class));  // channel2's eviction event

        assertThat(createdInterestChannels.size(), is(2));
        assertThat(createdInterestChannels.get(1), instanceOf(Sourced.class));
        Source secondSource = (createdInterestChannels.get(1)).getSource();
        verifyRegistryContentContainOnlySource(registry, secondSource);
    }

}

package com.netflix.eureka2.integration.batching;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistryImpl;
import com.netflix.eureka2.client.interest.EurekaInterestClientImpl;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.IndexRegistry;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.InterestSetNotification;
import com.netflix.eureka2.protocol.discovery.SampleAddInstance;
import com.netflix.eureka2.protocol.discovery.StreamStateUpdate;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class IndexBatchMergeIntegrationTest {

    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    private final ReplaySubject<Object> incomingSubject = ReplaySubject.create();
    private final ReplaySubject<Void> serverConnectionLifecycle = ReplaySubject.create();

    private SourcedEurekaRegistry<InstanceInfo> registry;
    private MessageConnection serverConnection;
    private BatchingRegistry<InstanceInfo> remoteBatchingRegistry;
    private IndexRegistry<InstanceInfo> localIndexRegistry;
    private IndexRegistry<InstanceInfo> compositeIndexRegistry;
    private EurekaInterestClient interestClient;

    @Before
    public void setUp() {
        remoteBatchingRegistry = new BatchingRegistryImpl<>();
        localIndexRegistry = new IndexRegistryImpl<>();
        compositeIndexRegistry = new BatchAwareIndexRegistry<>(localIndexRegistry, remoteBatchingRegistry);

        registry = new SourcedEurekaRegistryImpl(compositeIndexRegistry, EurekaRegistryMetricFactory.registryMetrics());

        serverConnection = mock(MessageConnection.class);
        when(serverConnection.incoming()).thenReturn(incomingSubject);
        when(serverConnection.acknowledge()).thenReturn(Observable.<Void>empty());
        when(serverConnection.lifecycleObservable()).thenReturn(serverConnectionLifecycle);

        TransportClient transport = mock(TransportClient.class);
        when(transport.connect()).thenReturn(Observable.just(serverConnection));

        ClientChannelFactory<InterestChannel> channelFactory = new InterestChannelFactory(
                transport,
                registry,
                remoteBatchingRegistry,
                EurekaClientMetricFactory.clientMetrics()
        );

        interestClient = new EurekaInterestClientImpl(registry, channelFactory);
    }

    @Test
    public void testNewInterestServerHasData() throws Exception {
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

        when(serverConnection.submitWithAck(Mockito.anyObject())).then(new Answer<Observable<Void>>() {
            @Override
            public Observable<Void> answer(InvocationOnMock invocation) throws Throwable {
                remoteData.subscribe(incomingSubject);
                return Observable.empty();
            }
        });

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

    private List<ChangeNotification<InstanceInfo>> toChangeNotifications(List<InterestSetNotification> interestSetNotifications) {
        List<ChangeNotification<InstanceInfo>> toReturn = new ArrayList<>(interestSetNotifications.size());
        for (InterestSetNotification n : interestSetNotifications) {
            if (n instanceof StreamStateUpdate) {
                if (((StreamStateUpdate) n).getState() == StreamStateNotification.BufferState.BufferEnd) {
                    toReturn.add(ChangeNotification.<InstanceInfo>bufferSentinel());
                } else {
                    // ignore buffer start
                }
            } else if (n instanceof AddInstance) {
                toReturn.add(new ChangeNotification<>(ChangeNotification.Kind.Add, ((AddInstance) n).getInstanceInfo()));
            }
        }

        return toReturn;
    }

    private StreamStateUpdate newBufferStart(Interest<InstanceInfo> interest) {
        return new StreamStateUpdate(StreamStateNotification.bufferStartNotification(interest));
    }

    private StreamStateUpdate newBufferEnd(Interest<InstanceInfo> interest) {
        return new StreamStateUpdate(StreamStateNotification.bufferEndNotification(interest));
    }
}

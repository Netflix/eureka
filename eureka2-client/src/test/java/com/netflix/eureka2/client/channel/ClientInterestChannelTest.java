package com.netflix.eureka2.client.channel;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.netflix.eureka2.channel.InterestChannel.STATE;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.EurekaRegistryImpl;
import com.netflix.eureka2.registry.index.IndexRegistry;
import com.netflix.eureka2.registry.index.IndexRegistryImpl;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.protocol.common.AddInstance;
import com.netflix.eureka2.spi.protocol.common.DeleteInstance;
import com.netflix.eureka2.spi.protocol.common.InterestSetNotification;
import com.netflix.eureka2.spi.protocol.interest.SampleAddInstance;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

/**
 * @author David Liu
 */
public class ClientInterestChannelTest {

    private final TestScheduler testScheduler = Schedulers.test();

    protected EurekaConnection serverConnection = mock(EurekaConnection.class);
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final ReplaySubject<Void> serverConnectionLifecycle = ReplaySubject.create();

    protected TransportClient transportClient = mock(TransportClient.class);

    protected IndexRegistry<InstanceInfo> indexRegistry = new IndexRegistryImpl<>();
    protected EurekaRegistry<InstanceInfo> registry = new EurekaRegistryImpl(indexRegistry, registryMetrics(), testScheduler);

    protected InterestChannelMetrics interestChannelMetrics = mock(InterestChannelMetrics.class);

    protected ClientInterestChannel channel;

    protected Interest<InstanceInfo> sampleInterestZuul = SampleInterest.ZuulApp.build();
    protected Observable<AddInstance> sampleAddMessagesZuul = SampleAddInstance.newMessages(SampleAddInstance.ZuulAdd, 2);

    protected Interest<InstanceInfo> sampleInterestDiscovery = SampleInterest.DiscoveryApp.build();
    protected Observable<AddInstance> sampleAddMessagesDiscovery = SampleAddInstance.newMessages(SampleAddInstance.DiscoveryAdd, 2);

    protected Interest<InstanceInfo> sampleInterestAll = Interests.forFullRegistry();

    @Before
    public void setup() throws Throwable {
        when(serverConnection.incoming()).thenReturn(incomingSubject);
        when(serverConnection.acknowledge()).thenReturn(Observable.<Void>empty());
        when(serverConnection.submitWithAck(Mockito.anyObject())).thenReturn(Observable.<Void>empty());
        when(serverConnection.lifecycleObservable()).thenReturn(serverConnectionLifecycle);
        when(transportClient.connect()).thenReturn(Observable.just(serverConnection));

        channel = new ClientInterestChannel(registry, transportClient, 0, interestChannelMetrics);
    }

    @After
    public void tearDown() {
        channel.close();
        registry.shutdown();
    }

    @Test(timeout = 60000)
    public void testChangeWithFirstInterest() throws Exception {
        // Subscriber
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestZuul).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnCompleted();

        // Send subscription data
        sendInput(sampleAddMessagesZuul);
        testScheduler.triggerActions();

        // Now fetch registry content, and verify reply
        assertForInterestReturns(sampleInterestZuul, sampleAddMessagesZuul);
    }

    @Test(timeout = 60000)
    public void testChangeWithSubsequentInterest() throws Exception {
        // Subscribe to Zuul, and send Zuul change notifications
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestZuul).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnCompleted();

        sendInput(sampleAddMessagesZuul);
        testScheduler.triggerActions();

        // Subscribe to Discovery, and send Zuul change notifications
        testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestDiscovery).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnCompleted();

        sendInput(sampleAddMessagesDiscovery);
        testScheduler.triggerActions();

        // Check that the registry contains both sets
        assertForInterestReturns(sampleInterestZuul, sampleAddMessagesZuul);
        assertForInterestReturns(sampleInterestDiscovery, sampleAddMessagesDiscovery);
    }

    @Test(timeout = 60000)
    public void testCleanUpResourcesOnClose() throws Exception {
        // Subscriber
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestZuul).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnCompleted();

        // Close the channel and check that no more subscriptions are allowed
        channel.close();

        testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestAll).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnError();
    }

    @Test(timeout = 60000)
    public void testTransportDelete() throws Exception {
        // preload the channel cache and registry with data
        InstanceInfo original1 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo original2 = SampleInstanceInfo.ZuulServer.build();
        AddInstance message1 = ProtocolModel.getDefaultModel().newAddInstance(original1);
        AddInstance message2 = ProtocolModel.getDefaultModel().newAddInstance(original2);

        DeleteInstance message3 = ProtocolModel.getDefaultModel().newDeleteInstance(original1.getId());

        // Subscribe
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestZuul).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnCompleted();

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry()).filter(dataOnlyFilter()).subscribe(notificationSubscriber);

        // Send to add change notifications
        incomingSubject.onNext(message1);
        testScheduler.triggerActions();
        assertThat(notificationSubscriber.takeNextOrWait(), addChangeNotificationOf(original1));

        incomingSubject.onNext(message2);
        testScheduler.triggerActions();
        assertThat(notificationSubscriber.takeNextOrWait(), addChangeNotificationOf(original2));

        // Now remove first item
        incomingSubject.onNext(message3);
        testScheduler.triggerActions();
        assertThat(notificationSubscriber.takeNextOrWait(), deleteChangeNotificationOf(original1));
    }

    @Test
    public void testBufferingHintsPropagation() throws Exception {
        InstanceInfo original1 = SampleInstanceInfo.DiscoveryServer.build();
        InstanceInfo original2 = SampleInstanceInfo.DiscoveryServer.build();
        AddInstance message1 = ProtocolModel.getDefaultModel().newAddInstance(original1);
        AddInstance message2 = ProtocolModel.getDefaultModel().newAddInstance(original2);
        Interest<InstanceInfo> interest = Interests.forVips(original1.getVipAddress());

        // Subscribe first
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(interest).subscribe(testSubscriber);

        TestSubscriber<ChangeNotification<InstanceInfo>> networkInterestSubscriber = new TestSubscriber<>();
        channel.channelInterestStream.subscribe(networkInterestSubscriber);

        // a subscriber that subscribes to the registry before it has any data
        TestSubscriber<ChangeNotification<InstanceInfo>> registrySubscriber = new TestSubscriber<>();
        registry.forInterest(interest).subscribe(registrySubscriber);

        // Issue batch of data
        incomingSubject.onNext(ProtocolModel.getDefaultModel().newStreamStateUpdate(StreamStateNotification.bufferStartNotification(interest)));
        incomingSubject.onNext(message1);
        incomingSubject.onNext(message2);
        incomingSubject.onNext(ProtocolModel.getDefaultModel().newStreamStateUpdate(StreamStateNotification.bufferEndNotification(interest)));
        testScheduler.triggerActions();

        // check the conversion from network to channel level datastructures
        assertThat(networkInterestSubscriber.getOnNextEvents().size(), is(4));
        assertThat(networkInterestSubscriber.getOnNextEvents().get(0), is(bufferStartNotification()));
        assertThat(networkInterestSubscriber.getOnNextEvents().get(1), is(addChangeNotificationOf(original1)));
        assertThat(networkInterestSubscriber.getOnNextEvents().get(2), is(addChangeNotificationOf(original2)));
        assertThat(networkInterestSubscriber.getOnNextEvents().get(3), is(bufferEndNotification()));

        // check the returns from the registry

        assertThat(registrySubscriber.getOnNextEvents().size(), is(6));
        // first two are bufferStart and bufferEnd of the "LOCAL" source
        assertThat(registrySubscriber.getOnNextEvents().get(0), is(bufferStartNotification()));
        assertThat(registrySubscriber.getOnNextEvents().get(1), is(bufferEndNotification()));
        assertThat(registrySubscriber.getOnNextEvents().get(2), is(bufferStartNotification()));
        List<InstanceInfo> received = Arrays.asList(
                registrySubscriber.getOnNextEvents().get(3).getData(),
                registrySubscriber.getOnNextEvents().get(4).getData()
        );
        assertThat(received, containsInAnyOrder(original1, original2));
        assertThat(registrySubscriber.getOnNextEvents().get(5), is(bufferEndNotification()));
    }

    @Test(timeout = 60000)
    public void testMetrics() throws Exception {
        // Subscribe to interest subscription, to open the channel
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestZuul).subscribe(testSubscriber);

        testScheduler.triggerActions();
        verify(interestChannelMetrics, times(1)).incrementStateCounter(STATE.Open);

        // Shutdown channel
        channel.close();
        testScheduler.triggerActions();
        verify(interestChannelMetrics, times(1)).decrementStateCounter(STATE.Open);
        verify(interestChannelMetrics, times(1)).incrementStateCounter(STATE.Closed);
    }

    private void assertForInterestReturns(Interest<InstanceInfo> interest, Observable<AddInstance> addMessages) throws InterruptedException {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> updatesSubscriber = new ExtTestSubscriber<>();
        registry.forInterest(interest).subscribe(updatesSubscriber);

        Collection<InstanceInfo> expected = from(addMessages);
        updatesSubscriber.assertProducesInAnyOrder(expected, new Func1<ChangeNotification<InstanceInfo>, InstanceInfo>() {
            @Override
            public InstanceInfo call(ChangeNotification<InstanceInfo> notification) {
                return notification.getData();
            }
        });
    }

    private static List<InstanceInfo> from(Observable<AddInstance> observable) {
        return observable.map(new Func1<AddInstance, InstanceInfo>() {
            @Override
            public InstanceInfo call(AddInstance addInstance) {
                return addInstance.getInstanceInfo();
            }
        }).toList().toBlocking().first();
    }

    private void sendInput(Observable<? extends InterestSetNotification> updates) {
        updates.subscribe(new Action1<InterestSetNotification>() {
            @Override
            public void call(InterestSetNotification addInstance) {
                incomingSubject.onNext(addInstance);
            }
        });
    }
}

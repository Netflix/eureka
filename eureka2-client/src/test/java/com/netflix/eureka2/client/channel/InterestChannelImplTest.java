package com.netflix.eureka2.client.channel;

import java.util.Collection;
import java.util.List;

import com.netflix.eureka2.channel.InterestChannel.STATE;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.DeleteInstance;
import com.netflix.eureka2.protocol.discovery.InterestSetNotification;
import com.netflix.eureka2.protocol.discovery.SampleAddInstance;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInterest;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class InterestChannelImplTest {

    private final TestScheduler testScheduler = Schedulers.test();

    protected MessageConnection serverConnection = mock(MessageConnection.class);
    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final ReplaySubject<Void> serverConnectionLifecycle = ReplaySubject.create();

    protected TransportClient transportClient = mock(TransportClient.class);

    protected SourcedEurekaRegistry<InstanceInfo> registry = new SourcedEurekaRegistryImpl(registryMetrics(), testScheduler);

    protected InterestChannelMetrics interestChannelMetrics = mock(InterestChannelMetrics.class);

    protected InterestChannelImpl channel;

    protected Interest<InstanceInfo> sampleInterestZuul = SampleInterest.ZuulApp.build();
    protected Observable<AddInstance> sampleAddMessagesZuul = SampleAddInstance.newMessages(SampleAddInstance.ZuulAdd, 2);

    protected Interest<InstanceInfo> sampleInterestDiscovery = SampleInterest.DiscoveryApp.build();
    protected Observable<AddInstance> sampleAddMessagesDiscovery = SampleAddInstance.newMessages(SampleAddInstance.DiscoveryAdd, 2);

    protected Interest<InstanceInfo> sampleInterestAll = Interests.forFullRegistry();

    protected Observable<AddInstance> sampleAddMessagesAll = sampleAddMessagesZuul.concatWith(sampleAddMessagesDiscovery);

    @Before
    public void setup() throws Throwable {
        when(serverConnection.incoming()).thenReturn(incomingSubject);
        when(serverConnection.acknowledge()).thenReturn(Observable.<Void>empty());
        when(serverConnection.submitWithAck(Mockito.anyObject())).thenReturn(Observable.<Void>empty());
        when(serverConnection.lifecycleObservable()).thenReturn(serverConnectionLifecycle);
        when(transportClient.connect()).thenReturn(Observable.just(serverConnection));

        channel = new InterestChannelImpl(registry, transportClient, interestChannelMetrics);
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
        AddInstance message1 = new AddInstance(original1);
        AddInstance message2 = new AddInstance(original2);

        DeleteInstance message3 = new DeleteInstance(original1.getId());

        // Subscribe
        ExtTestSubscriber<Void> testSubscriber = new ExtTestSubscriber<>();
        channel.change(sampleInterestZuul).subscribe(testSubscriber);

        testScheduler.triggerActions();
        testSubscriber.assertOnCompleted();

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        registry.forInterest(Interests.forFullRegistry()).subscribe(notificationSubscriber);

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

    @Test(timeout = 60000)
    public void testMetrics() throws Exception {
        // Subscriber to interest subscription, to open the channel
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

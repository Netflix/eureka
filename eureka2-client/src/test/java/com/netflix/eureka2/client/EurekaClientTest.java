package com.netflix.eureka2.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.InterestHandler;
import com.netflix.eureka2.client.interest.InterestHandlerImpl;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.protocol.discovery.StreamStateUpdate;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;
import rx.subjects.ReplaySubject;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.changeNotificationBatchOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
@RunWith(MockitoJUnitRunner.class)
public class EurekaClientTest {

    private final MessageConnection mockConnection = mock(MessageConnection.class);
    private final TransportClient mockReadTransportClient = mock(TransportClient.class);

    private final Source localSource = new Source(Source.Origin.LOCAL);

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

            registry = spy(new PreservableEurekaRegistry(
                    new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics()),
                    new BasicEurekaRegistryConfig(),
                    EurekaRegistryMetricFactory.registryMetrics()));
            for (ChangeNotification<InstanceInfo> notification : allRegistry) {
                registry.register(notification.getData(), localSource).toBlocking().firstOrDefault(null);
            }

            when(mockConnection.submitWithAck(anyObject())).thenReturn(Observable.<Void>empty());
            when(mockConnection.acknowledge()).thenReturn(Observable.<Void>empty());
            StreamStateUpdate stateUpdate = new StreamStateUpdate(StreamStateNotification.finishBufferingNotification(interestAll));
            when(mockConnection.incoming()).thenReturn(Observable.<Object>just(stateUpdate));
            when(mockConnection.lifecycleObservable()).thenReturn(ReplaySubject.<Void>create());
            when(mockReadTransportClient.connect()).thenReturn(Observable.just(mockConnection));

            ClientChannelFactory<InterestChannel> interestChannelFactory = new InterestChannelFactory(
                    mockReadTransportClient,
                    registry,
                    EurekaClientMetricFactory.clientMetrics()
            );

            InterestHandler interestHandler = new InterestHandlerImpl(registry, interestChannelFactory);

            client = new EurekaClientImpl(interestHandler, null);
        }

        @Override
        protected void after() {
            client.close();
        }
    };

    // =======================
    // interest path tests
    // =======================

    @Test(timeout = 60000)
    public void testForInterestSingleUser() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        client.forInterest(interestAll).subscribe(testSubscriber);

        List<ChangeNotification<InstanceInfo>> output = testSubscriber.takeNextOrWait(allRegistry.size() + 2);
        assertThat(output, is(changeNotificationBatchOf(allRegistry)));
    }

    @Test(timeout = 60000)
    public void testForInterestSameTwoUsers() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber1 = new ExtTestSubscriber<>();
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber2 = new ExtTestSubscriber<>();

        Observable<ChangeNotification<InstanceInfo>> notificationObservable = client.forInterest(interestAll);

        notificationObservable.subscribe(testSubscriber1);
        notificationObservable.subscribe(testSubscriber2);

        List<ChangeNotification<InstanceInfo>> output1 = testSubscriber1.takeNextOrWait(allRegistry.size() + 2);
        assertThat(output1, is(changeNotificationBatchOf(allRegistry)));
        List<ChangeNotification<InstanceInfo>> output2 = testSubscriber2.takeNextOrWait(allRegistry.size() + 2);
        assertThat(output2, is(changeNotificationBatchOf(allRegistry)));
    }

    @Test(timeout = 60000)
    public void testForInterestDifferentTwoUsers() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber1 = new ExtTestSubscriber<>();
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber2 = new ExtTestSubscriber<>();

        client.forInterest(interestDiscovery).subscribe(testSubscriber1);
        client.forInterest(interestZuul).subscribe(testSubscriber2);

        List<ChangeNotification<InstanceInfo>> output1 = testSubscriber1.takeNextOrWait(discoveryRegistry.size() + 2);
        assertThat(output1, is(changeNotificationBatchOf(discoveryRegistry)));
        List<ChangeNotification<InstanceInfo>> output2 = testSubscriber2.takeNextOrWait(zuulRegistry.size() + 2);
        assertThat(output2, is(changeNotificationBatchOf(zuulRegistry)));
    }
}

package com.netflix.eureka2.client;

import com.netflix.eureka2.client.EurekaMembershipSource.DefaultMapper;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.InstanceInfo.Builder;
import com.netflix.eureka2.registry.InstanceInfo.Status;
import com.netflix.eureka2.registry.SampleInstanceInfo;
import netflix.ocelli.Host;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.MembershipEvent.EventType;
import org.junit.Before;
import org.junit.Test;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaMembershipSourceTest {

    private static final InstanceInfo APP = SampleInstanceInfo.ZuulServer.build();
    private static final Host APP_HOST = new DefaultMapper().call(APP);

    private final EurekaClient eurekaClient = mock(EurekaClient.class);
    private EurekaMembershipSource membershipSource;
    private final PublishSubject<ChangeNotification<InstanceInfo>> interestObservable = PublishSubject.create();

    private final TestSubscriber<MembershipEvent<Host>> testSubscriber = new TestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        when(eurekaClient.forInterest(any(Interest.class))).thenReturn(interestObservable);
        membershipSource = new EurekaMembershipSource(eurekaClient);
        membershipSource.forVip(APP.getVipAddress()).subscribe(testSubscriber);
    }

    @Test
    public void testConvertsAddNotificationToMembershipEvents() throws Exception {
        interestObservable.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, APP));
        testSubscriber.assertReceivedOnNext(singletonList(new MembershipEvent<Host>(EventType.ADD, APP_HOST)));
    }

    @Test
    public void testConvertsModifyNotificationToMembershipEvents() throws Exception {
        // Modify (== add for service up)
        interestObservable.onNext(new ModifyNotification<InstanceInfo>(APP, null));

        InstanceInfo appDown = new Builder().withInstanceInfo(APP).withStatus(Status.DOWN).build();
        interestObservable.onNext(new ModifyNotification<InstanceInfo>(appDown, null));

        testSubscriber.assertReceivedOnNext(asList(
                new MembershipEvent<Host>(EventType.ADD, APP_HOST),
                new MembershipEvent<Host>(EventType.REMOVE, APP_HOST)
        ));
    }

    @Test
    public void testConvertsDeleteNotificationToMembershipEvents() throws Exception {
        interestObservable.onNext(new ChangeNotification<InstanceInfo>(Kind.Delete, APP));
        testSubscriber.assertReceivedOnNext(singletonList(new MembershipEvent<Host>(EventType.REMOVE, APP_HOST)));
    }
}
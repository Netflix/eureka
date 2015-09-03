package com.netflix.eureka2.utils.rx;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.TestName;
import rx.functions.Action1;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 * @author Nitesh Kant
 */
public class PauseableSubjectTest {

    private PauseableSubject<ChangeNotification<InstanceInfo>> notificationsSubject;
    private List<ChangeNotification<InstanceInfo>> receivedNotifications;

    private ChangeNotification<InstanceInfo> discoveryAdd = SampleChangeNotification.DiscoveryAdd.newNotification();
    private ChangeNotification<InstanceInfo> zuulAdd = SampleChangeNotification.ZuulAdd.newNotification();

    @Rule public TestName testName = new TestName();
    @Rule public final ExternalResource subjectResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            notificationsSubject = PauseableSubject.create();
            receivedNotifications = new ArrayList<>();
            notificationsSubject.subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                @Override
                public void call(ChangeNotification<InstanceInfo> notification) {
                    receivedNotifications.add(notification);
                }
            });

            discoveryAdd = SampleChangeNotification.DiscoveryAdd.newNotification();
            zuulAdd = SampleChangeNotification.ZuulAdd.newNotification();
        }

        @Override
        protected void after() {
            notificationsSubject.onCompleted();
            receivedNotifications.clear();
        }
    };

    @Test(timeout = 60000)
    public void testNoPause() throws Exception {
        notificationsSubject.onNext(discoveryAdd);
        notificationsSubject.onNext(zuulAdd);

        assertThat(receivedNotifications, hasSize(2));
        assertThat(receivedNotifications, contains(discoveryAdd, zuulAdd)); // Checks the order of notifications.
    }

    @Test(timeout = 60000)
    public void testPause() throws Exception {
        notificationsSubject.onNext(discoveryAdd);
        notificationsSubject.onNext(zuulAdd);

        assertThat(receivedNotifications, hasSize(2));
        assertThat(receivedNotifications, contains(discoveryAdd, zuulAdd)); // Checks the order of notifications.

        receivedNotifications.clear(); // Reset before pause so that assertion is easier later.

        assertThat(notificationsSubject.isPaused(), is(false));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));

        notificationsSubject.onNext(zuulAdd);

        assertThat(receivedNotifications, hasSize(0));
        notificationsSubject.resume();
        assertThat(notificationsSubject.isPaused(), is(false));

        assertThat(receivedNotifications, hasSize(1));
        assertThat(receivedNotifications, contains(zuulAdd)); // Checks the order of notifications.
    }

    @Test(timeout = 60000)
    public void testCompleteWhilePaused() throws Exception {

        assertThat(notificationsSubject.isPaused(), is(false));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));

        notificationsSubject.onNext(zuulAdd);
        notificationsSubject.onCompleted();
        notificationsSubject.onNext(discoveryAdd); // Should not honor this.

        assertThat(receivedNotifications, hasSize(0));
        notificationsSubject.resume();
        assertThat(notificationsSubject.isPaused(), is(false));

        assertThat(receivedNotifications, hasSize(1));
        assertThat(receivedNotifications, contains(zuulAdd)); // Checks the order of notifications.
    }

    @Test(timeout = 60000)
    public void testOnErrorWhilePaused() throws Exception {

        assertThat(notificationsSubject.isPaused(), is(false));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));

        notificationsSubject.onNext(zuulAdd);
        notificationsSubject.onError(new NullPointerException());
        notificationsSubject.onNext(discoveryAdd); // Should not honor this.

        assertThat(receivedNotifications, hasSize(0));
        notificationsSubject.resume();
        assertThat(notificationsSubject.isPaused(), is(false));

        assertThat(receivedNotifications, hasSize(1));
        assertThat(receivedNotifications, contains(zuulAdd)); // Checks the order of notifications.
    }

    @Test(timeout = 60000)
    public void testResumeResults() throws Exception {
        assertThat(notificationsSubject.isPaused(), is(false));
        assertThat(notificationsSubject.resume(), is(PauseableSubject.ResumeResult.NotPaused));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));
        assertThat(notificationsSubject.resume(), is(PauseableSubject.ResumeResult.Resumed));
    }
}

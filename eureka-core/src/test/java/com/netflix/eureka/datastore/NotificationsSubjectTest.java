package com.netflix.eureka.datastore;

import com.netflix.eureka.ChangeNotifications;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
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
public class NotificationsSubjectTest {

    private NotificationsSubject<InstanceInfo> notificationsSubject;
    private List<ChangeNotification<InstanceInfo>> receivedNotifications;

    @Rule public TestName testName = new TestName();
    @Rule public final ExternalResource subjectResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            notificationsSubject = NotificationsSubject.create();
            receivedNotifications = new ArrayList<ChangeNotification<InstanceInfo>>();
            notificationsSubject.subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                @Override
                public void call(ChangeNotification<InstanceInfo> notification) {
                    receivedNotifications.add(notification);
                }
            });
        }

        @Override
        protected void after() {
            notificationsSubject.onCompleted();
            receivedNotifications.clear();
        }
    };

    @Test
    public void testNoPause() throws Exception {
        notificationsSubject.onNext(ChangeNotifications.DiscoveryAddNotification);
        notificationsSubject.onNext(ChangeNotifications.ZuulAddNotification);

        assertThat(receivedNotifications, hasSize(2));
        assertThat(receivedNotifications, contains(ChangeNotifications.DiscoveryAddNotification,
                                                   ChangeNotifications.ZuulAddNotification)); // Checks the order of notifications.
    }

    @Test
    public void testPause() throws Exception {
        notificationsSubject.onNext(ChangeNotifications.DiscoveryAddNotification);
        notificationsSubject.onNext(ChangeNotifications.ZuulAddNotification);

        assertThat(receivedNotifications, hasSize(2));
        assertThat(receivedNotifications, contains(ChangeNotifications.DiscoveryAddNotification,
                                                   ChangeNotifications.ZuulAddNotification)); // Checks the order of notifications.

        receivedNotifications.clear(); // Reset before pause so that assertion is easier later.

        assertThat(notificationsSubject.isPaused(), is(false));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));

        notificationsSubject.onNext(ChangeNotifications.ZuulAddNotification);

        assertThat(receivedNotifications, hasSize(0));
        notificationsSubject.resume();
        assertThat(notificationsSubject.isPaused(), is(false));

        assertThat(receivedNotifications, hasSize(1));
        assertThat(receivedNotifications, contains(ChangeNotifications.ZuulAddNotification)); // Checks the order of notifications.
    }

    @Test
    public void testCompleteWhilePaused() throws Exception {

        assertThat(notificationsSubject.isPaused(), is(false));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));

        notificationsSubject.onNext(ChangeNotifications.ZuulAddNotification);
        notificationsSubject.onCompleted();
        notificationsSubject.onNext(ChangeNotifications.DiscoveryAddNotification); // Should not honor this.

        assertThat(receivedNotifications, hasSize(0));
        notificationsSubject.resume();
        assertThat(notificationsSubject.isPaused(), is(false));

        assertThat(receivedNotifications, hasSize(1));
        assertThat(receivedNotifications, contains(ChangeNotifications.ZuulAddNotification)); // Checks the order of notifications.
    }

    @Test
    public void testOnErrorWhilePaused() throws Exception {

        assertThat(notificationsSubject.isPaused(), is(false));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));

        notificationsSubject.onNext(ChangeNotifications.ZuulAddNotification);
        notificationsSubject.onError(new NullPointerException());
        notificationsSubject.onNext(ChangeNotifications.DiscoveryAddNotification); // Should not honor this.

        assertThat(receivedNotifications, hasSize(0));
        notificationsSubject.resume();
        assertThat(notificationsSubject.isPaused(), is(false));

        assertThat(receivedNotifications, hasSize(1));
        assertThat(receivedNotifications, contains(ChangeNotifications.ZuulAddNotification)); // Checks the order of notifications.
    }

    @Test
    public void testResumeResults() throws Exception {
        assertThat(notificationsSubject.isPaused(), is(false));
        assertThat(notificationsSubject.resume(), is(NotificationsSubject.ResumeResult.NotPaused));
        notificationsSubject.pause();
        assertThat(notificationsSubject.isPaused(), is(true));
        assertThat(notificationsSubject.resume(), is(NotificationsSubject.ResumeResult.Resumed));
    }
}

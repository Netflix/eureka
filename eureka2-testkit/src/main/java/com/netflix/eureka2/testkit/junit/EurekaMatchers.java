package com.netflix.eureka2.testkit.junit;

import java.util.List;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.testkit.junit.matchers.ChangeNotificationBatchMatcher;
import com.netflix.eureka2.testkit.junit.matchers.ChangeNotificationKindMatcher;
import com.netflix.eureka2.testkit.junit.matchers.ChangeNotificationMatcher;
import com.netflix.eureka2.testkit.junit.matchers.InstanceInfoMatcher;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

/**
 * @author Tomasz Bak
 */
public final class EurekaMatchers {

    private EurekaMatchers() {
    }

    public static Matcher<InstanceInfo> sameInstanceInfoAs(InstanceInfo expectedValue) {
        return new InstanceInfoMatcher(expectedValue);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> addChangeNotificationOf(InstanceInfo expectedValue) {
        return new ChangeNotificationMatcher(Kind.Add, expectedValue);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> addChangeNotification() {
        return new ChangeNotificationMatcher(Kind.Add);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> modifyChangeNotificationOf(InstanceInfo expectedValue) {
        return new ChangeNotificationMatcher(Kind.Modify, expectedValue);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> deleteChangeNotificationOf(InstanceInfo expectedValue) {
        return new ChangeNotificationMatcher(Kind.Delete, expectedValue);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> bufferingChangeNotification() {
        return new ChangeNotificationKindMatcher(Kind.BufferSentinel);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> bufferStartNotification() {
        return new BaseMatcher<ChangeNotification<InstanceInfo>>() {
            @Override
            public boolean matches(Object item) {
                if (!new ChangeNotificationKindMatcher(Kind.BufferSentinel).matches(item)) {
                    return false;
                } else {
                    return ((StreamStateNotification<InstanceInfo>) item).getBufferState() == StreamStateNotification.BufferState.BufferStart;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Expected bufferStart notification");
            }
        };
    }

    public static Matcher<ChangeNotification<InstanceInfo>> bufferEndNotification() {
        return new BaseMatcher<ChangeNotification<InstanceInfo>>() {
            @Override
            public boolean matches(Object item) {
                if (!new ChangeNotificationKindMatcher(Kind.BufferSentinel).matches(item)) {
                    return false;
                } else {
                    return ((StreamStateNotification<InstanceInfo>) item).getBufferState() == StreamStateNotification.BufferState.BufferEnd;
                }
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Expected bufferStart notification");
            }
        };
    }

    public static <T> Matcher<List<ChangeNotification<T>>> changeNotificationBatchOf(List<ChangeNotification<T>> dataNotifications) {
        return new ChangeNotificationBatchMatcher<T>(dataNotifications);
    }
}

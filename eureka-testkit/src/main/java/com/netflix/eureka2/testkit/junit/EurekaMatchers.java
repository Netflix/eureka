package com.netflix.eureka2.testkit.junit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.junit.matchers.ChangeNotificationMatcher;
import com.netflix.eureka2.testkit.junit.matchers.InstanceInfoMatcher;
import org.hamcrest.Matcher;

/**
 * @author Tomasz Bak
 */
public final class EurekaMatchers {

    private EurekaMatchers() {
    }

    public static Matcher<InstanceInfo> sameInstanceInfoAs(InstanceInfo expectedValue) {
        return new InstanceInfoMatcher(expectedValue, false);
    }

    public static Matcher<InstanceInfo> identicalInstanceInfoAs(InstanceInfo expectedValue) {
        return new InstanceInfoMatcher(expectedValue, true);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> addChangeNotificationOf(InstanceInfo expectedValue) {
        return new ChangeNotificationMatcher(Kind.Add, expectedValue);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> modifyChangeNotificationOf(InstanceInfo expectedValue) {
        return new ChangeNotificationMatcher(Kind.Modify, expectedValue);
    }

    public static Matcher<ChangeNotification<InstanceInfo>> deleteChangeNotificationOf(InstanceInfo expectedValue) {
        return new ChangeNotificationMatcher(Kind.Delete, expectedValue);
    }
}

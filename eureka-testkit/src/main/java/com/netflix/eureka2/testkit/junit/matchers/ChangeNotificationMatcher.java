package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.junit.EurekaMatchers;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationMatcher extends BaseMatcher<ChangeNotification<InstanceInfo>> {

    private final Kind notificationKind;
    private final InstanceInfo expectedValue;

    public ChangeNotificationMatcher(Kind notificationKind, InstanceInfo expectedValue) {
        this.notificationKind = notificationKind;
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean matches(Object item) {
        if (!(item instanceof ChangeNotification)) {
            return false;
        }
        if (!(((ChangeNotification<?>) item).getData() instanceof InstanceInfo)) {
            return false;
        }
        ChangeNotification<InstanceInfo> actualNotif = (ChangeNotification<InstanceInfo>) item;
        if (actualNotif.getKind() != notificationKind) {
            return false;
        }

        return EurekaMatchers.sameInstanceInfoAs(expectedValue).matches(actualNotif.getData());
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Change notification of type " + notificationKind).appendValue(expectedValue);
    }
}

package com.netflix.eureka2.testkit.junit.matchers;

import java.util.Arrays;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationKindMatcher extends BaseMatcher<ChangeNotification<InstanceInfo>> {

    private final Kind[] notificationKinds;

    public ChangeNotificationKindMatcher(Kind... notificationKinds) {
        this.notificationKinds = notificationKinds;
    }

    @Override
    public boolean matches(Object item) {
        if (!(item instanceof ChangeNotification)) {
            return false;
        }
        ChangeNotification<InstanceInfo> notification = (ChangeNotification<InstanceInfo>) item;
        for (Kind kind : notificationKinds) {
            if (notification.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("Expected change notification kind of " + Arrays.toString(notificationKinds));
    }
}
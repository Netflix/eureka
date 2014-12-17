package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class ChangeNotificationMatcherTest {

    private static final InstanceInfo INFO = SampleInstanceInfo.EurekaWriteServer.build();
    private static final InstanceInfo OTHER_INFO = SampleInstanceInfo.EurekaReadServer.build();

    @Test
    public void testAddNotificationMatcher() throws Exception {
        // Matches same
        boolean result = addChangeNotificationOf(INFO).matches(new ChangeNotification<>(Kind.Add, INFO));
        assertThat("Add change notification should match", result, is(true));

        // Fail on different
        result = addChangeNotificationOf(INFO).matches(new ChangeNotification<>(Kind.Modify, INFO));
        assertThat("Add change notification should fail", result, is(false));

        result = addChangeNotificationOf(INFO).matches(new ChangeNotification<>(Kind.Add, OTHER_INFO));
        assertThat("Add change notification should fail", result, is(false));
    }

    @Test
    public void testModifyNotificationMatcher() throws Exception {
        // Matches same
        boolean result = modifyChangeNotificationOf(INFO).matches(new ChangeNotification<>(Kind.Modify, INFO));
        assertThat("Modify change notification should match", result, is(true));
    }

    @Test
    public void testDeleteNotificationMatcher() throws Exception {
        // Matches same
        boolean result = deleteChangeNotificationOf(INFO).matches(new ChangeNotification<>(Kind.Delete, INFO));
        assertThat("Delete change notification should match", result, is(true));
    }
}
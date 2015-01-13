package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * @author Tomasz Bak
 */
public class InstanceInfoMatcher extends BaseMatcher<InstanceInfo> {
    private final InstanceInfo expectedValue;

    public InstanceInfoMatcher(InstanceInfo expectedValue) {
        this.expectedValue = expectedValue;
    }

    @Override
    public boolean matches(Object item) {
        if (!(item instanceof InstanceInfo)) {
            return false;
        }

        InstanceInfo target = (InstanceInfo) item;
        return target.equals(expectedValue);
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(expectedValue);
    }
}

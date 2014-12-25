package com.netflix.eureka2.testkit.junit.matchers;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

/**
 * @author Tomasz Bak
 */
public class InstanceInfoMatcher extends BaseMatcher<InstanceInfo> {
    private final InstanceInfo expectedValue;
    private final boolean expectSameVersion;

    public InstanceInfoMatcher(InstanceInfo expectedValue, boolean expectSameVersion) {
        this.expectedValue = expectedValue;
        this.expectSameVersion = expectSameVersion;
    }

    @Override
    public boolean matches(Object item) {
        if (!(item instanceof InstanceInfo)) {
            return false;
        }

        // Versions may be different
        InstanceInfo target = (InstanceInfo) item;
        if (!expectSameVersion) {
            target = new Builder()
                    .withInstanceInfo(target)
                    .withVersion(expectedValue.getVersion())
                    .build();
        }

        return target.equals(expectedValue);
    }

    @Override
    public void describeTo(Description description) {
        description.appendValue(expectedValue);
    }
}

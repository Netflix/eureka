package com.netflix.eureka2.server.service.overrides;

import java.util.Arrays;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;
import rx.observers.TestSubscriber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author David Liu
 */
public class InMemoryStatusOverridesRegistryTest {

    private final InMemoryStatusOverridesRegistry overridesSource = new InMemoryStatusOverridesRegistry();
    private final TestSubscriber<Boolean> testSubscriber1 = new TestSubscriber<>();
    private final TestSubscriber<Boolean> testSubscriber2 = new TestSubscriber<>();

    private final InstanceInfo info1 = SampleInstanceInfo.DiscoveryServer.build();
    private final InstanceInfo info2 = SampleInstanceInfo.ZuulServer.build();

    @Test
    public void testLifecycle() {
        overridesSource.shouldApplyOutOfService(info1).subscribe(testSubscriber1);
        overridesSource.shouldApplyOutOfService(info2).subscribe(testSubscriber2);

        overridesSource.setOutOfService(info1).subscribe();
        overridesSource.unsetOutOfService(info1).subscribe();

        overridesSource.setOutOfService(info2).subscribe();
        overridesSource.unsetOutOfService(info2).subscribe();

        overridesSource.setOutOfService(info1).subscribe();

        assertThat(testSubscriber1.getOnNextEvents(), equalTo(Arrays.asList(false, true, false, true)));
        assertThat(testSubscriber2.getOnNextEvents(), equalTo(Arrays.asList(false, true, false)));
    }

    @Test
    public void testLifecycleMidSubscription() {
        overridesSource.setOutOfService(info1).subscribe();
        overridesSource.unsetOutOfService(info1).subscribe();

        overridesSource.setOutOfService(info2).subscribe();

        overridesSource.shouldApplyOutOfService(info1).subscribe(testSubscriber1);
        overridesSource.shouldApplyOutOfService(info2).subscribe(testSubscriber2);

        overridesSource.unsetOutOfService(info2).subscribe();

        overridesSource.setOutOfService(info1).subscribe();

        assertThat(testSubscriber1.getOnNextEvents(), equalTo(Arrays.asList(false, true)));
        assertThat(testSubscriber2.getOnNextEvents(), equalTo(Arrays.asList(true, false)));
    }
}

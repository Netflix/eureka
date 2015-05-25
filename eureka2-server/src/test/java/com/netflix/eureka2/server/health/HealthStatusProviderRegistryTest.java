package com.netflix.eureka2.server.health;

import java.util.List;

import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class HealthStatusProviderRegistryTest {

    @Test
    public void testReturnsDefaultHealthStatusProviderIfNonInjected() throws Exception {
        HealthStatusProviderRegistry registry = new HealthStatusProviderRegistry();

        ExtTestSubscriber<List<HealthStatusProvider<?>>> testSubscriber = new ExtTestSubscriber<>();
        registry.healthStatusProviders().subscribe(testSubscriber);

        registry.activate();

        List<HealthStatusProvider<?>> statusProviders = testSubscriber.takeNextOrFail();
        assertThat(statusProviders.size(), is(1));

        ExtTestSubscriber<HealthStatusUpdate<?>> healthSubscriber = new ExtTestSubscriber<>();
        statusProviders.get(0).healthStatus().subscribe(healthSubscriber);

        assertThat(healthSubscriber.takeNext().getStatus(), is(equalTo(Status.UP)));
    }
}
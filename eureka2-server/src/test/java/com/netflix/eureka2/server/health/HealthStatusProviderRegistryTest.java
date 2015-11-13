package com.netflix.eureka2.server.health;

import java.util.List;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.eureka2.health.AbstractHealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.server.module.EurekaHealthStatusModule;
import com.netflix.governator.Governator;
import com.netflix.governator.LifecycleInjector;
import org.junit.Test;

import javax.inject.Singleton;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class HealthStatusProviderRegistryTest {

    @Test
    public void testReturnsDefaultHealthStatusProviderIfNonInjected() throws Exception {
        LifecycleInjector injector = Governator.createInjector(new EurekaHealthStatusModule());
        try {
            HealthStatusProviderRegistry registry = injector.getInstance(HealthStatusProviderRegistry.class);

            ExtTestSubscriber<List<HealthStatusProvider<?>>> testSubscriber = new ExtTestSubscriber<>();
            registry.healthStatusProviders().subscribe(testSubscriber);

            registry.activate();

            List<HealthStatusProvider<?>> statusProviders = testSubscriber.takeNextOrFail();
            assertThat(statusProviders.size(), is(1));

            ExtTestSubscriber<HealthStatusUpdate<?>> healthSubscriber = new ExtTestSubscriber<>();
            statusProviders.get(0).healthStatus().subscribe(healthSubscriber);

            assertThat(healthSubscriber.takeNext().getStatus(), is(equalTo(Status.UP)));

        } finally {
            injector.shutdown();
        }
    }

    @Test
    public void testReturnsSpecificHealthStatusProvidersIfSomeAreInjected() throws Exception {
        LifecycleInjector injector = Governator.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(TestHealthStatusProvider1.class).in(Scopes.SINGLETON);
                bind(TestHealthStatusProvider2.class).in(Scopes.SINGLETON);
                install(new EurekaHealthStatusModule());

            }
        });

        try {
            HealthStatusProviderRegistry registry = injector.getInstance(HealthStatusProviderRegistry.class);

            ExtTestSubscriber<List<HealthStatusProvider<?>>> testSubscriber = new ExtTestSubscriber<>();
            registry.healthStatusProviders().subscribe(testSubscriber);

            registry.activate();

            List<HealthStatusProvider<?>> statusProviders = testSubscriber.takeNextOrFail();
            assertThat(statusProviders.size(), is(2));

            ExtTestSubscriber<HealthStatusUpdate<?>> healthSubscriber = new ExtTestSubscriber<>();
            statusProviders.get(0).healthStatus().subscribe(healthSubscriber);

            assertThat(healthSubscriber.takeNext().getStatus(), is(equalTo(Status.DOWN)));

            healthSubscriber = new ExtTestSubscriber<>();
            statusProviders.get(1).healthStatus().subscribe(healthSubscriber);

            assertThat(healthSubscriber.takeNext().getStatus(), is(equalTo(Status.STARTING)));

        } finally {
            injector.shutdown();
        }
    }

    @Singleton
    static class TestHealthStatusProvider1 extends AbstractHealthStatusProvider<TestHealthStatusProvider1> {

        private static final SubsystemDescriptor<TestHealthStatusProvider1> DESCRIPTOR = new SubsystemDescriptor<>(
                TestHealthStatusProvider1.class,
                "test provider 1",
                "test provider 1"
        );

        protected TestHealthStatusProvider1() {
            super(Status.DOWN, DESCRIPTOR);
        }
    }

    @Singleton
    static class TestHealthStatusProvider2 extends AbstractHealthStatusProvider<TestHealthStatusProvider2> {

        private static final SubsystemDescriptor<TestHealthStatusProvider2> DESCRIPTOR = new SubsystemDescriptor<>(
                TestHealthStatusProvider2.class,
                "test provider 2",
                "test provider 2"
        );

        protected TestHealthStatusProvider2() {
            super(Status.STARTING, DESCRIPTOR);
        }
    }
}
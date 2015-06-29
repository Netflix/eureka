package com.netflix.eureka2.server.module;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import com.netflix.governator.auto.annotations.ConditionalOnClass;
import com.netflix.spectator.api.Clock;
import com.netflix.spectator.api.ExtendedRegistry;
import com.netflix.spectator.metrics3.MetricsRegistry;

/**
 * @author David Liu
 */
@ConditionalOnClass("com.codahale.metrics.MetricRegistry")
public class SpectatorCodahaleMetricsModule extends AbstractModule {
    @Override
    protected void configure() {
        MetricRegistry internalRegistry = new MetricRegistry();
        final JmxReporter reporter = JmxReporter.forRegistry(internalRegistry).build();
        reporter.start();

        ExtendedRegistry registry = new ExtendedRegistry(new MetricsRegistry(Clock.SYSTEM, internalRegistry));
        bind(ExtendedRegistry.class).toInstance(registry);
    }
}

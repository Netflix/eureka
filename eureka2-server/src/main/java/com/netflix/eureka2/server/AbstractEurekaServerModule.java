package com.netflix.eureka2.server;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.SpectatorEurekaRegistryMetricFactory;
import com.netflix.eureka2.metric.client.EurekaClientMetricFactory;
import com.netflix.eureka2.metric.client.SpectatorEurekaClientMetricFactory;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.metric.server.SpectatorEurekaServerMetricFactory;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregatorImpl;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.http.HealthConnectionHandler;
import io.reactivex.netty.metrics.MetricEventsListenerFactory;
import io.reactivex.netty.spectator.SpectatorEventsListenerFactory;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaServerModule extends AbstractModule {

    //
    // split the bindings into logical groups for more modularity
    //

    protected void bindBase() {
        bind(EurekaHttpServer.class).asEagerSingleton();
        bind(EurekaHealthStatusAggregator.class).to(EurekaHealthStatusAggregatorImpl.class).asEagerSingleton();
        bind(HealthConnectionHandler.class).asEagerSingleton();
    }

    protected void bindMetricFactories() {
        bind(EurekaClientMetricFactory.class).to(SpectatorEurekaClientMetricFactory.class).asEagerSingleton();
        bind(EurekaServerMetricFactory.class).to(SpectatorEurekaServerMetricFactory.class).asEagerSingleton();
        bind(EurekaRegistryMetricFactory.class).to(SpectatorEurekaRegistryMetricFactory.class).asEagerSingleton();
    }

    protected void bindInterestComponents() {
        bind(MetricEventsListenerFactory.class)
                .annotatedWith(Names.named(com.netflix.eureka2.Names.INTEREST))
                .toInstance(new SpectatorEventsListenerFactory("discovery-rx-client-", "discovery-rx-server-"));
    }
}

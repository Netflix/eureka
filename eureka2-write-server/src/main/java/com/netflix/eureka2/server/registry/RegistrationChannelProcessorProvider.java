package com.netflix.eureka2.server.registry;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.overrides.OverridesRegistry;
import com.netflix.eureka2.server.service.overrides.OverridesServiceImpl;

/**
 * @author Tomasz Bak
 */
public class RegistrationChannelProcessorProvider implements Provider<EurekaRegistrationProcessor> {

    private final OverridesServiceImpl overridesService;
    private final PreservableRegistryProcessor preservableRegistrationProcessor;

    @Inject
    public RegistrationChannelProcessorProvider(SourcedEurekaRegistry sourcedEurekaRegistry,
                                                OverridesRegistry overridesRegistry,
                                                EvictionQuotaProvider evictionQuotaProvider,
                                                EurekaRegistryMetricFactory metricFactory) {
        this.overridesService = new OverridesServiceImpl(sourcedEurekaRegistry, overridesRegistry);
        this.preservableRegistrationProcessor = new PreservableRegistryProcessor(
                overridesService,
                evictionQuotaProvider,
                metricFactory
        );
    }

    @PreDestroy
    public void shutdown() {
        overridesService.shutdown();
        preservableRegistrationProcessor.shutdown();
    }

    @Override
    public EurekaRegistrationProcessor<InstanceInfo> get() {
        return preservableRegistrationProcessor;
    }
}

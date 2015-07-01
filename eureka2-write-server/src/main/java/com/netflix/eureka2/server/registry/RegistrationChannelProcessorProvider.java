package com.netflix.eureka2.server.registry;

import javax.annotation.PreDestroy;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.google.inject.Inject;
import com.netflix.eureka2.Names;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.overrides.OverridesService;

/**
 * @author Tomasz Bak
 */
@Singleton
public class RegistrationChannelProcessorProvider implements Provider<EurekaRegistrationProcessor> {

    private final EurekaRegistrationProcessor sourcedEurekaRegistry;
    private final OverridesService overridesService;
    private final EvictionQuotaKeeper evictionQuotaKeeper;
    private final EurekaRegistryMetricFactory metricFactory;

    private volatile PreservableRegistryProcessor preservableRegistrationProcessor;

    // Since
    public static class OptionalOverridesService {
        @Inject(optional = true) OverridesService arg;

        public void setArg(OverridesService arg) {  // useful for testing
            this.arg = arg;
        }
    }

    @Inject
    public RegistrationChannelProcessorProvider(@Named(Names.REGISTRY) EurekaRegistrationProcessor sourcedEurekaRegistry,
                                                OptionalOverridesService optionalOverridesService,
                                                EvictionQuotaKeeper evictionQuotaKeeper,
                                                EurekaRegistryMetricFactory metricFactory) {
        this.sourcedEurekaRegistry = sourcedEurekaRegistry;
        this.overridesService = optionalOverridesService.arg;
        this.evictionQuotaKeeper = evictionQuotaKeeper;
        this.metricFactory = metricFactory;
    }

    @PreDestroy
    public void shutdown() {
        if (preservableRegistrationProcessor != null) {
            preservableRegistrationProcessor.shutdown();
        }
    }

    @Override
    public synchronized EurekaRegistrationProcessor<InstanceInfo> get() {
        if (preservableRegistrationProcessor == null) {
            preservableRegistrationProcessor = new PreservableRegistryProcessor(
                    combine(sourcedEurekaRegistry, overridesService),
                    evictionQuotaKeeper,
                    metricFactory
            );
        }
        return preservableRegistrationProcessor;
    }

    private static EurekaRegistrationProcessor<InstanceInfo> combine(EurekaRegistrationProcessor sourcedEurekaRegistry, OverridesService overrideService) {
        if (overrideService == null) {
            return sourcedEurekaRegistry;
        }

        overrideService.addOutboundHandler(sourcedEurekaRegistry);
        return overrideService;
    }
}

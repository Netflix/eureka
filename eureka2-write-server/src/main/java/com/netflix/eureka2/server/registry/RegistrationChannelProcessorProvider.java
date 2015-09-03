package com.netflix.eureka2.server.registry;

import javax.annotation.PreDestroy;
import javax.inject.Provider;
import javax.inject.Singleton;

import com.google.inject.Inject;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.service.overrides.OverridesService;

/**
 * @author Tomasz Bak
 */
@Singleton
public class RegistrationChannelProcessorProvider implements Provider<EurekaRegistrationProcessor> {

    private final EurekaRegistrationProcessor registrationProcessor;
    private final OverridesService overridesService;

    private volatile EurekaRegistrationProcessor<InstanceInfo> finalRegistrationProcessor;

    // Since
    public static class OptionalOverridesService {
        @Inject(optional = true) OverridesService arg;

        public void setArg(OverridesService arg) {  // useful for testing
            this.arg = arg;
        }
    }

    @Inject
    public RegistrationChannelProcessorProvider(EurekaRegistrationProcessor registrationProcessor,
                                                OptionalOverridesService optionalOverridesService) {
        this.registrationProcessor = registrationProcessor;
        this.overridesService = optionalOverridesService.arg;
    }

    @PreDestroy
    public void shutdown() {
        if (finalRegistrationProcessor != null) {
            finalRegistrationProcessor.shutdown();
        }
    }

    @Override
    public synchronized EurekaRegistrationProcessor<InstanceInfo> get() {
        if (finalRegistrationProcessor == null) {
            finalRegistrationProcessor = combine(registrationProcessor, overridesService);
        }
        return finalRegistrationProcessor;
    }

    private static EurekaRegistrationProcessor<InstanceInfo> combine(EurekaRegistrationProcessor<InstanceInfo> processor, OverridesService overrideService) {
        if (overrideService == null) {
            return processor;
        }

        overrideService.addOutboundHandler(processor);
        return overrideService;
    }
}

package com.netflix.eureka2.server.registry;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

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

    private final PreservableRegistryProcessor preservableRegistrationProcessor;

    @Inject
    public RegistrationChannelProcessorProvider(@Named(Names.REGISTRY) EurekaRegistrationProcessor sourcedEurekaRegistry,
                                                Map<Integer, OverridesService> overrideServices,
                                                EvictionQuotaKeeper evictionQuotaKeeper,
                                                EurekaRegistryMetricFactory metricFactory) {
        this.preservableRegistrationProcessor = new PreservableRegistryProcessor(
                combine(sourcedEurekaRegistry, overrideServices),
                evictionQuotaKeeper,
                metricFactory
        );
    }

    @PreDestroy
    public void shutdown() {
        preservableRegistrationProcessor.shutdown();
    }

    @Override
    public EurekaRegistrationProcessor<InstanceInfo> get() {
        return preservableRegistrationProcessor;
    }

    private static OverridesService combine(EurekaRegistrationProcessor sourcedEurekaRegistry, Map<Integer, OverridesService> overrideServices) {
        if (overrideServices.isEmpty()) {
            throw new IllegalArgumentException("No override service provided");
        }
        TreeMap<Integer, OverridesService> sorted = new TreeMap<>(overrideServices);
        Iterator<OverridesService> it = sorted.values().iterator();
        OverridesService head = it.next();
        OverridesService tail = head;
        while (it.hasNext()) {
            OverridesService next = it.next();
            tail.addOutboundHandler(next);
            tail = next;
        }
        tail.addOutboundHandler(sourcedEurekaRegistry);
        return head;
    }
}

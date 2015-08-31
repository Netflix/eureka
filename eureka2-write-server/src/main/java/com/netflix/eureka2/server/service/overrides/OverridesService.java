package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.registry.RegistrationChannelProcessorProvider;

/**
 * {@link EurekaRegistrationProcessor} implementation that decorates registry entries with overrides provided
 * outside of the registration channel. It maintains internal cache of successful registrations, which holds the
 * original {@link InstanceInfo} objects as received from the channel. Overrides are provided as a reactive stream,
 * which is merged with channel input.
 * <h1>Self preservation mode</h1>
 * If the underlying registry enters self preservation mode, it is mandatory to keep updating registry entries, even
 * if the associated registration channels are disconnected. Otherwise, it would not be possible to take broken servers
 * out of service or do other critical overrides. Due to that, this service removes {@link InstanceInfo} objects
 * from the cache only after their local copies have been successfully removed from the registry.
 */
public interface OverridesService extends EurekaRegistrationProcessor<InstanceInfo> {

    /**
     * {@link OverridesService} instances are created via Guice, at which stage they are not ready yet to
     * be assembled into a command chain. {@link RegistrationChannelProcessorProvider} is injected with all
     * {@link OverridesService} implementations and assembles them together using this method.
     */
    void addOutboundHandler(EurekaRegistrationProcessor<InstanceInfo> outboundHandler);
}

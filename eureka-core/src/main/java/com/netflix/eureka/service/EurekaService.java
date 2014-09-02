package com.netflix.eureka.service;

import com.netflix.eureka.registry.EurekaRegistry;

/**
 * A service for Eureka that provides access to the underlying {@link EurekaRegistry}.
 *
 * Any access to the registry is provided via a {@link ServiceChannel} which encompasses the eureka protocol for that
 * operation.
 *
 * @author Nitesh Kant
 */
public interface EurekaService {

    /**
     * Returns a new {@link InterestChannel}.
     *
     * @return A new {@link InterestChannel}.
     */
    InterestChannel newInterestChannel();

    /**
     * Returns a new {@link RegistrationChannel}.
     *
     * @return A new {@link RegistrationChannel}.
     */
    RegistrationChannel newRegistrationChannel();

    /**
     * Shutdown this service.
     */
    void shutdown();
}

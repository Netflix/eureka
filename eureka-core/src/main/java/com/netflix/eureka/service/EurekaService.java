package com.netflix.eureka.service;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;

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
     * Returns a {@link InterestChannel} associated with the passed {@code interest}.
     *
     * @param interest Interest to which the invoker is interested in receiving {@link ChangeNotification}s
     *
     * @return An {@link InterestChannel} corresponding to the passed {@code interest}
     */
    InterestChannel forInterest(Interest<InstanceInfo> interest);

    /**
     * Returns a {@link RegistrationChannel} for the registration of the passed {@code instanceToRegister}.
     *
     * @param instanceToRegister Instance to register.
     *
     * @return A {@link RegistrationChannel} for the registration of the passed {@code instanceToRegister}.
     */
    RegistrationChannel forRegistration(InstanceInfo instanceToRegister);
}

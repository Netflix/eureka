package com.netflix.discovery;

import com.netflix.appinfo.ApplicationInfoManager;

/**
 * A handler that can be registered with an {@link EurekaClient} at creation time to execute
 * pre registration logic. The pre registration logic need to be synchronous to be guaranteed
 * to execute before registration.
 */
public interface PreRegistrationHandler {
    void beforeRegistration();
}

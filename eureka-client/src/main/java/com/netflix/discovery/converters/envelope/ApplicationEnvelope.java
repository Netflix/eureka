package com.netflix.discovery.converters.envelope;

import com.netflix.discovery.shared.Application;

/**
 * A wrapper for {@link Application} object used to serialize/deserialize
 * REST level entity.
 *
 * @author Tomasz Bak
 */
public class ApplicationEnvelope {

    private final Application application;

    ApplicationEnvelope() {
        this.application = null;
    }

    public ApplicationEnvelope(Application application) {
        this.application = application;
    }

    public Application getApplication() {
        return application;
    }
}

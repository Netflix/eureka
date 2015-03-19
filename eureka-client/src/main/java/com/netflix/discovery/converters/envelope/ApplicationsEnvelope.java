package com.netflix.discovery.converters.envelope;

import com.netflix.discovery.shared.Applications;

/**
 * A wrapper for {@link Applications} object used to serialize/deserialize
 * REST level entity.
 *
 * @author Tomasz Bak
 */
public class ApplicationsEnvelope {

    private final Applications applications;

    ApplicationsEnvelope() {
        this.applications = null;
    }

    public ApplicationsEnvelope(Applications applications) {
        this.applications = applications;
    }

    public Applications getApplications() {
        return applications;
    }
}

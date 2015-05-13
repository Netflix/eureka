package com.netflix.discovery.converters.envelope;

import com.netflix.appinfo.InstanceInfo;

/**
 * A wrapper for {@link InstanceInfo} object used to serialize/deserialize
 * REST level entity.
 *
 * @author Tomasz Bak
 */
public class InstanceInfoEnvelope {

    private final InstanceInfo instance;

    protected InstanceInfoEnvelope() {
        this.instance = null;
    }

    public InstanceInfoEnvelope(InstanceInfo instance) {
        this.instance = instance;
    }

    public InstanceInfo getInstance() {
        return instance;
    }
}

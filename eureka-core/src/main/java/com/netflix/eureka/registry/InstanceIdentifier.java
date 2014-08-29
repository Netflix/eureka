package com.netflix.eureka.registry;

import com.netflix.eureka.datastore.Item;

/**
 * @author David Liu
 */
public class InstanceIdentifier implements Item {

    private final String id;

    private InstanceIdentifier() {
        id = null;
    }

    public InstanceIdentifier(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Long getVersion() {
        return null;
    }
}

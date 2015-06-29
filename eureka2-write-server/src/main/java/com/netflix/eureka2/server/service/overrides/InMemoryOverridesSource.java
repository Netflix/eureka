package com.netflix.eureka2.server.service.overrides;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David Liu
 */
public class InMemoryOverridesSource implements LoadingOverridesRegistry.ExternalOverridesSource {
    ConcurrentMap<String, Overrides> externalSource = new ConcurrentHashMap<>();

    @Override
    public void set(Overrides overrides) throws Exception {
        externalSource.put(overrides.getId(), overrides);
    }

    @Override
    public void remove(String id) throws Exception {
        externalSource.remove(id);
    }

    @Override
    public Map<String, Overrides> asMap() {
        return Collections.unmodifiableMap(externalSource);
    }
}

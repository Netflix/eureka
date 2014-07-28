package com.netflix.eureka.protocol.registration;

import java.util.Map;

/**
 * @author Tomasz Bak
 */
public class Update {
    private final Map<String, String> fieldUpdates;

    public Update(Map<String, String> fieldUpdates) {
        this.fieldUpdates = fieldUpdates;
    }

    public Map<String, String> getFieldUpdates() {
        return fieldUpdates;
    }
}

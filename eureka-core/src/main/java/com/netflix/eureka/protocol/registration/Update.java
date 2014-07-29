package com.netflix.eureka.protocol.registration;

/**
 * @author Tomasz Bak
 */
public class Update {
    private final String key;
    private final String value;

    // For serialization framework
    protected Update() {
        key = value = null;
    }

    public Update(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}

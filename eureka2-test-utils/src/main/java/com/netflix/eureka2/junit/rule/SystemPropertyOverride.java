package com.netflix.eureka2.junit.rule;

import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class SystemPropertyOverride extends ExternalResource {

    private final String key;
    private final String value;

    private boolean modified;
    private String originalValue;

    public SystemPropertyOverride(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    protected void before() throws Throwable {
        originalValue = System.setProperty(key, value);
        modified = true;
    }

    @Override
    protected void after() {
        if (modified) {
            if (originalValue == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, originalValue);
            }
        }
    }
}

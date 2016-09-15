package com.netflix.discovery.internal.util;

import com.netflix.archaius.api.Config;

import java.util.Iterator;

/**
 * An internal only module to help with config loading with prefixes, due to the fact Archaius2 config's
 * getPrefixedView() has odd behaviour when config substitution gets involved.
 *
 * @author David Liu
 */
public final class InternalPrefixedConfig {
    private final Config config;
    private final String namespace;

    public InternalPrefixedConfig(Config config, String... namespaces) {
        this.config = config;
        String tempNamespace = "";
        for (String namespace : namespaces) {
            if (namespace != null && !namespace.isEmpty()) {
                tempNamespace += namespace.endsWith(".")
                        ? namespace
                        : namespace + ".";
            }
        }

        this.namespace = tempNamespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getString(String key, String defaultValue) {
        return config.getString(namespace + key, defaultValue);
    }

    public Integer getInteger(String key, Integer defaultValue) {
        return config.getInteger(namespace + key, defaultValue);
    }

    public Long getLong(String key, Long defaultValue) {
        return config.getLong(namespace + key, defaultValue);
    }

    public Double getDouble(String key, Double defaultValue) {
        return config.getDouble(namespace + key, defaultValue);
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        return config.getBoolean(namespace + key, defaultValue);
    }

    public Iterator<String> getKeys() {
        final String prefixRegex = "^" + namespace;
        final Iterator<String> internalIterator = config.getKeys(namespace);
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return internalIterator.hasNext();
            }

            @Override
            public String next() {
                String value = internalIterator.next();
                return value.replaceFirst(prefixRegex, "");
            }
        };
    }
}

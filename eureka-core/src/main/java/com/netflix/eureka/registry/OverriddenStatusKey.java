package com.netflix.eureka.registry;

import java.util.Objects;

/**
 * Key for {@link AbstractInstanceRegistry#overriddenInstanceStatusMap}.
 *
 * The map used to be keyed by a bare instance id, so any two applications registering
 * instances with the same, entirely client-chosen id string would silently share a single
 * status override entry, letting one application flip another's status without ever
 * writing outside its own {@code appName} namespace. Scoping the key to
 * {@code (appName, id)} keeps the override table's isolation in line with every other
 * appName-scoped path in the registry.
 */
public final class OverriddenStatusKey {

    private final String appName;
    private final String id;

    public OverriddenStatusKey(String appName, String id) {
        this.appName = appName;
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OverriddenStatusKey)) {
            return false;
        }
        OverriddenStatusKey that = (OverriddenStatusKey) o;
        return Objects.equals(appName, that.appName) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appName, id);
    }

    @Override
    public String toString() {
        return appName + "/" + id;
    }
}

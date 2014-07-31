package com.netflix.eureka.protocol.discovery;

/**
 * @author Tomasz Bak
 */
public class UpdateInstanceInfo {

    private final String key;
    private final String value;

    // For serialization framework
    protected UpdateInstanceInfo() {
        key = value = null;
    }

    public UpdateInstanceInfo(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UpdateInstanceInfo that = (UpdateInstanceInfo) o;

        if (key != null ? !key.equals(that.key) : that.key != null) {
            return false;
        }
        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "UpdateInstanceInfo{key='" + key + '\'' + ", value='" + value + '\'' + '}';
    }
}

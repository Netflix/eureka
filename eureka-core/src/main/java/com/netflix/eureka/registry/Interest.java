package com.netflix.eureka.registry;

/**
 * An interest can be specified as some matching value on a given index
 * @author David Liu
 */
public class Interest {

    private final Index index;
    private final String value;

    protected Interest() {
        index = null;
        value = null;
    }

    public Interest(Index index, String value) {
        this.index = index;
        this.value = value;
    }

    public Index getIndex() {
        return index;
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

        Interest interest = (Interest) o;

        if (index != interest.index) {
            return false;
        }
        if (value != null ? !value.equals(interest.value) : interest.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = index != null ? index.hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Interest{index=" + index + ", value='" + value + '\'' + '}';
    }
}

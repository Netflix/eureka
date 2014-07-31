package com.netflix.eureka.transport.base;

/**
* @author Tomasz Bak
*/
public class SampleUserObject {
    private String value1;
    private int value2;

    public SampleUserObject() {
    }

    public SampleUserObject(String value1, int value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    @Override
    public int hashCode() {
        int result = value1 != null ? value1.hashCode() : 0;
        result = 31 * result + value2;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SampleUserObject that = (SampleUserObject) o;

        if (value2 != that.value2) return false;
        if (value1 != null ? !value1.equals(that.value1) : that.value1 != null) return false;

        return true;
    }
}

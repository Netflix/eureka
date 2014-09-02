package com.netflix.eureka.transport.base;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import com.netflix.eureka.transport.Acknowledgement;
import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectData;

/**
 * @author Tomasz Bak
 */
public class SampleObject {

    public static final Set<Class<?>> SAMPLE_OBJECT_MODEL_SET = Collections.<Class<?>>singleton(SampleObject.class);

    private Internal[] internals;

    public SampleObject() {
    }

    public SampleObject(Internal... internals) {
        this.internals = internals;
    }


    public Internal[] getInternal() {
        return internals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SampleObject that = (SampleObject) o;

        if (!Arrays.equals(internals, that.internals)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return internals != null ? Arrays.hashCode(internals) : 0;
    }

    @Override
    public String toString() {
        return "SampleObject{internals=" + Arrays.toString(internals) + '}';
    }

    public static Schema rootSchema() {
        ReflectData reflectData = new ReflectData();
        return Schema.createUnion(Arrays.asList(
                reflectData.getSchema(SampleObject.class),
                reflectData.getSchema(Acknowledgement.class)));
    }

    public static class Internal {
        private String value;

        public Internal() {
        }

        public Internal(String value) {
            this.value = value;
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

            Internal internal = (Internal) o;

            if (value != null ? !value.equals(internal.value) : internal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "Internal{value='" + value + '\'' + '}';
        }
    }
}

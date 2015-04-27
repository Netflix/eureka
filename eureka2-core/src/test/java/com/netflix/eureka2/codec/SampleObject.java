package com.netflix.eureka2.codec;

import com.netflix.eureka2.transport.Acknowledgement;
import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectData;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Tomasz Bak
 */
public class SampleObject {

    public static final Set<Class<?>> SAMPLE_OBJECT_MODEL_SET = Collections.<Class<?>>singleton(SampleObject.class);

    public static final SampleObject CONTENT;

    static {
        Map<String, String> mapValue = new HashMap<>();
        mapValue.put("keyA", "valueA");
        CONTENT = new SampleObject(new Internal("stringValue", mapValue));
    }

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

        private Map<String, String> mapValue;

        public Internal() {
        }

        public Internal(String value) {
            this.value = value;
            this.mapValue = Collections.emptyMap();
        }

        public Internal(Map<String, String> mapValue) {
            this.mapValue = mapValue;
        }

        public Internal(String value, Map<String, String> mapValue) {
            this.value = value;
            this.mapValue = mapValue;
        }

        public String getValue() {
            return value;
        }

        public Map<String, String> getMapValue() {
            return mapValue;
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

            if (mapValue != null ? !mapValue.equals(internal.mapValue) : internal.mapValue != null) {
                return false;
            }
            if (value != null ? !value.equals(internal.value) : internal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = value != null ? value.hashCode() : 0;
            result = 31 * result + (mapValue != null ? mapValue.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Internal{value='" + value + '\'' + ", mapValue=" + mapValue + '}';
        }
    }
}

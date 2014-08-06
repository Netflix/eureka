package com.netflix.eureka.transport.base;

import java.util.Arrays;

import com.netflix.eureka.transport.utils.TransportModel;
import com.netflix.eureka.transport.utils.TransportModel.TransportModelBuilder;

/**
 * @author Tomasz Bak
 */
public class SampleObject {

    public static final TransportModel TRANSPORT_MODEL =
            new TransportModelBuilder(SampleObject.class)
                    .withHierarchy(Internal.class, InternalA.class, InternalB.class)
                    .build();

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
    public String toString() {
        return "SampleObject{internals=" + Arrays.toString(internals) + '}';
    }

    @Override
    public int hashCode() {
        return internals != null ? Arrays.hashCode(internals) : 0;
    }

    public static interface Internal {
    }

    public static class InternalA implements Internal {
        private String value;

        public InternalA() {
        }

        public InternalA(String value) {
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

            InternalA internalA = (InternalA) o;

            if (value != null ? !value.equals(internalA.value) : internalA.value != null) {
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
            return "InternalA{value='" + value + '\'' + '}';
        }
    }

    public static class InternalB implements Internal {
        private int value;

        public InternalB() {
        }

        public InternalB(int value) {
            this.value = value;
        }

        public int getValue() {
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

            InternalB internalB = (InternalB) o;

            if (value != internalB.value) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public String toString() {
            return "InternalB{value=" + value + '}';
        }
    }
}

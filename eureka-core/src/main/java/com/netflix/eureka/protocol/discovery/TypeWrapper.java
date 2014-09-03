package com.netflix.eureka.protocol.discovery;

import java.util.HashSet;

/**
 * wrapper classes needed for avro serialization as union cannot handle "duplicates" between
 * HashSet<Integer> and HashSet<String>.
 * TODO: remove this if we use a different serialization method
 *
 * @author David Liu
 */
interface TypeWrapper<T> {

    T getValue();

    static final class HashSetInt implements TypeWrapper<HashSet<Integer>> {
        private HashSet<Integer> value;

        private HashSetInt() {} // for serializer

        HashSetInt(HashSet<Integer> value) {
            this.value = value;
        }

        @Override
        public HashSet<Integer> getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HashSetInt)) return false;

            HashSetInt that = (HashSetInt) o;

            if (value != null ? !value.equals(that.value) : that.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "HashSetInt{" +
                    "value=" + value +
                    '}';
        }
    }


    static final class HashSetString implements TypeWrapper<HashSet<String>> {
        private HashSet<String> value;

        private HashSetString() {} // for serializer

        HashSetString(HashSet<String> value) {
            this.value = value;
        }

        @Override
        public HashSet<String> getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HashSetString)) return false;

            HashSetString that = (HashSetString) o;

            if (value != null ? !value.equals(that.value) : that.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public String toString() {
            return "HashSetString{" +
                    "value=" + value +
                    '}';
        }
    }
}

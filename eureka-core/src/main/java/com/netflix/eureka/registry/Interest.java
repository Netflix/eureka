/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

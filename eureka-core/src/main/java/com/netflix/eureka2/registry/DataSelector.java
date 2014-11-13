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

package com.netflix.eureka2.registry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A base class for building custom query DSL over a collection or composite objects.
 *
 * @author Tomasz Bak
 */
public class DataSelector<T, S extends DataSelector<T,S>> {

    protected final List<List<Criteria<T, ?>>> queryStack = new ArrayList<>();
    protected List<Criteria<T, ?>> current = new ArrayList<>();

    DataSelector() {
        queryStack.add(current);
    }

    public S any() {
        if (!current.isEmpty()) {
            throw new IllegalStateException("Cannot apply any() as there are some criteria in the list");
        }
        return or();
    }

    public S or() {
        current = new ArrayList<>();
        queryStack.add(current);
        return (S) this;
    }

    protected Iterator<T> applyQuery(final Iterator<T> endpointIt) {
        return new Iterator<T>() {
            private T currentValue;

            @Override
            public boolean hasNext() {
                if (currentValue != null) {
                    return true;
                }
                while (endpointIt.hasNext()) {
                    T candidate = endpointIt.next();
                    if (matches(candidate)) {
                        currentValue = candidate;
                        return true;
                    }

                }
                return false;
            }

            @Override
            public T next() {
                return endpointIt.next();
            }

            @Override
            public void remove() {
                throw new IllegalStateException("Operation not supported");
            }
        };
    }

    private boolean matches(T candidate) {
        NEXT:
        for (List<Criteria<T, ?>> criterias : queryStack) {
            for (Criteria<T, ?> criteria : criterias) {
                if (!criteria.matches(candidate)) {
                    continue NEXT;
                }
            }
            return true;
        }
        return false;
    }


    abstract static class Criteria<T, V> {
        private final V[] values;

        @SafeVarargs
        Criteria(V... values) {
            this.values = values;
        }

        boolean matches(T endpoint) {
            for (V value : values) {
                if (matches(value, endpoint)) {
                    return true;
                }
            }
            return false;
        }

        protected abstract boolean matches(V value, T endpoint);
    }
}

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

package com.netflix.eureka2.registry.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * A base class for building custom query DSL over a collection of composite objects.
 *
 * @author Tomasz Bak
 */
public class DataSelector<T, S extends DataSelector<T, S>> {

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
        final FirstRunIterator firstRunIterator = new FirstRunIterator(endpointIt);
        return new Iterator<T>() {
            public SecondRunIterator secondRunIterator;

            @Override
            public boolean hasNext() {
                if (firstRunIterator.hasNext()) {
                    return true;
                }
                if (queryStack.size() < 2) {
                    return false;
                }
                if (secondRunIterator == null) {
                    secondRunIterator = new SecondRunIterator(firstRunIterator.getAvailableValues());
                }
                return secondRunIterator.hasNext();
            }

            @Override
            public T next() {
                if (hasNext()) {
                    return firstRunIterator.hasNext() ? firstRunIterator.next() : secondRunIterator.next();
                }
                throw new NoSuchElementException("no more elements");
            }

            @Override
            public void remove() {
                throw new IllegalStateException("Operation not supported");
            }
        };
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

    /**
     * To obey selector ordering rules, first we need to return all items matching
     * first part of an expression. For example in expression [part 1] OR [part 2],
     * this iterator will return only items matching [part 1], while or the subsequent
     * sub-queries will be cached, and later replayed by {@link SecondRunIterator}.
     */
    class FirstRunIterator implements Iterator<T> {

        private final Iterator<T> endpointIt;
        private final List<T>[] availableValues = new List[queryStack.size()];
        private T nextValue;

        FirstRunIterator(Iterator<T> endpointIt) {
            this.endpointIt = endpointIt;
        }

        public List<T>[] getAvailableValues() {
            return availableValues;
        }

        @Override
        public boolean hasNext() {
            if (nextValue != null) {
                return true;
            }
            while (endpointIt.hasNext()) {
                T candidate = endpointIt.next();
                if (matches(candidate, queryStack.get(0))) {
                    nextValue = candidate;
                    return true;
                } else {
                    for (int i = 1; i < queryStack.size(); i++) {
                        if (matches(candidate, queryStack.get(i))) {
                            List<T> queue = availableValues[i];
                            if (queue == null) {
                                queue = new LinkedList<>();
                                availableValues[i] = queue;
                            }
                            queue.add(candidate);
                        }
                    }
                }
            }
            return false;
        }

        @Override
        public T next() {
            if (hasNext()) {
                T toReturn = nextValue;
                nextValue = null;
                return toReturn;
            }
            throw new NoSuchElementException("no more elements");
        }

        @Override
        public void remove() {
            throw new IllegalStateException("Operation not supported");
        }

        private boolean matches(T candidate, List<Criteria<T, ?>> criterias) {
            for (Criteria<T, ?> criteria : criterias) {
                if (criteria.matches(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * In complex expression of the form [part 1] OR [part 2] OR ..., once we
     * return items matching [part 1], we need to return remaining matching items,
     * first matching [part 2], next [part 3], etc. {@link SecondRunIterator} is doing that.
     */
    class SecondRunIterator implements Iterator<T> {
        private final List<T>[] bufferedMatchedItems;
        private int replayPosition;
        private Iterator<T> replayPositionIterator = Collections.emptyIterator();

        SecondRunIterator(List<T>[] bufferedMatchedItems) {
            this.bufferedMatchedItems = bufferedMatchedItems;
        }

        @Override
        public boolean hasNext() {
            if (replayPosition >= bufferedMatchedItems.length) {
                return false;
            }
            if (replayPositionIterator.hasNext()) {
                return true;
            }
            for (replayPosition++; replayPosition < bufferedMatchedItems.length; replayPosition++) {
                List<T> items = bufferedMatchedItems[replayPosition];
                if (items != null && !items.isEmpty()) {
                    replayPositionIterator = items.iterator();
                    return true;
                }
            }
            return false;
        }

        @Override
        public T next() {
            if (hasNext()) {
                return replayPositionIterator.next();
            }
            throw new NoSuchElementException("no more elements");
        }

        @Override
        public void remove() {
            throw new IllegalStateException("Operation not supported");
        }
    }
}

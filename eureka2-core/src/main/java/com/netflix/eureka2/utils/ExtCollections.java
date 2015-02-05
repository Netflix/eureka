package com.netflix.eureka2.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author David Liu
 */
public final class ExtCollections {

    private ExtCollections() {
    }

    public static <T> HashSet<T> asSet(T... a) {
        HashSet<T> result = new HashSet<T>();
        Collections.addAll(result, a);
        return result;
    }

    public static <T> Iterator<T> singletonIterator(T value) {
        return Collections.singletonList(value).iterator();
    }

    @SafeVarargs
    public static <T> Iterator<T> concat(final Iterator<T>... iterators) {
        return new Iterator<T>() {

            private int current;
            private T next;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                if (current >= iterators.length) {
                    return false;
                }
                for (; current < iterators.length; current++) {
                    if (iterators[current].hasNext()) {
                        next = iterators[current].next();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public T next() {
                if (hasNext()) {
                    T result = next;
                    next = null;
                    return result;
                }
                throw new NoSuchElementException("no more elements");
            }

            @Override
            public void remove() {
                throw new IllegalStateException("operation not supported");
            }
        };
    }
}

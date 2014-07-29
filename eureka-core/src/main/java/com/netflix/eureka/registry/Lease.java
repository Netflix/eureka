package com.netflix.eureka.registry;

/**
 * Represent a lease over an element E
 * @author David Liu
 */
public class Lease<E> {

    private final E holder;

    public Lease(E holder) {
        this.holder = holder;
    }

    public E getHolder() {
        return holder;
    }

    public void renew(long durationMillis) {

    }

    public boolean hasExpired() {
        return false;
    }

    public void cancel() {

    }
}

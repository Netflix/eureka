package com.netflix.eureka2.interests;

/**
 * Eureka provides an interest based subscription model to subscribe for changes to eureka's registry, either locally
 * (directly with a local registry on client/server) or remote (remote server).
 * This interface identifies an interest.
 *
 * @author Nitesh Kant
 */
public interface Interest<T> {

    public enum Operator {Equals, Like}

    abstract boolean matches(T data);

    boolean isAtomicInterest();
}

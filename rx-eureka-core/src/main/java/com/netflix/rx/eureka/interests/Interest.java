package com.netflix.rx.eureka.interests;

/**
 * Eureka provides an interest based subscription model to subscribe for changes to eureka's registry, either locally
 * (directly with a local registry on client/server) or remote (remote server).
 * This class identifies an interest.
 *
 * @author Nitesh Kant
 */
public abstract class Interest<T> {

    public abstract boolean matches(T data);
}

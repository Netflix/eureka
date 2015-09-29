package com.netflix.eureka;

/**
 * Binds the Eureka server to a EIP, Route53 or else...
 */
public interface EurakaBinder {
    void bind() throws InterruptedException;
    void unbind() throws InterruptedException;
}

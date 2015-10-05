package com.netflix.eureka.aws;

/**
 * Binds the Eureka server to a EIP, Route53 or else...
 */
public interface AwsBinder {
    void start() throws Exception;
    void shutdown() throws Exception;
}
package com.netflix.eureka2.channel;

/**
 * @author David Liu
 */
public class RetryableEurekaChannelException extends Exception {

    public RetryableEurekaChannelException(String msg) {
        super(msg);
    }

    public RetryableEurekaChannelException(String msg, Throwable th) {
        super(msg, th);
    }
}

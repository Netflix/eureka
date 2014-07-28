package com.netflix.eureka.protocol;

/**
 * We assume that heart beats are at the application level, not embedded
 * into the transport layer/message broker.
 *
 * @author Tomasz Bak
 */
public class Heartbeat {
    public static final Heartbeat HEART_BEAT = new Heartbeat();
}

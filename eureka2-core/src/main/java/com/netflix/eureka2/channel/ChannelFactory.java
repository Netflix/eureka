package com.netflix.eureka2.channel;

/**
 * A generic channel factory interface for creating channels of a specified type
 *
 * @author David Liu
 */
public interface ChannelFactory<T extends ServiceChannel> {

    T newChannel();

    void shutdown();
}

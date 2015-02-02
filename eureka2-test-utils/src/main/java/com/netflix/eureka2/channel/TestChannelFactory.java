package com.netflix.eureka2.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author David Liu
 */
public class TestChannelFactory<T extends ServiceChannel> implements ChannelFactory<T> {
    private final ChannelFactory<T> delegate;

    private final Queue<T> channels;

    public TestChannelFactory(ChannelFactory<T> delegate) {
        this.delegate = delegate;
        this.channels = new ConcurrentLinkedQueue<>();
    }

    @Override
    public T newChannel() {
        T channel = delegate.newChannel();
        channels.add(channel);
        return channel;
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    public List<T> getAllChannels() {
        return new ArrayList<>(channels);
    }
}

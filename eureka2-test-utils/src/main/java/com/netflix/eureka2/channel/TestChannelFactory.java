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

    public T getLatestChannel() {
        List<T> allChannels = getAllChannels();
        if (allChannels == null || allChannels.isEmpty()) {
            return null;
        }
        return allChannels.get(allChannels.size()-1);
    }

    // poll and wait until the number of channels are created or the timeout is reached
    public boolean awaitChannels(int channelCount, int timeoutMillis) throws Exception {
        long timeoutTime = System.currentTimeMillis() + timeoutMillis;
        while(System.currentTimeMillis() < timeoutTime) {
            if (channels.size() == channelCount) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}

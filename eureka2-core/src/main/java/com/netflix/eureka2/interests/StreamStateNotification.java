package com.netflix.eureka2.interests;

/**
 * @author Tomasz Bak
 */
public class StreamStateNotification<T> extends ChangeNotification<T> {
    public StreamStateNotification(StreamState<T> streamState) {
        super(streamState);
    }
}

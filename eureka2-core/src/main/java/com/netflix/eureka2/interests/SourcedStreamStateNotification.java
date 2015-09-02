package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Sourced;

/**
 * @author David Liu
 */
public class SourcedStreamStateNotification<T> extends StreamStateNotification<T> implements Sourced {

    private final Source source;

    public SourcedStreamStateNotification(StreamStateNotification<T> notification, Source source) {
        this(notification.getBufferState(), notification.getInterest(), source);
    }

    public SourcedStreamStateNotification(BufferState bufferState, Interest<T> interest, Source source) {
        super(bufferState, interest);
        this.source = source;
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcedStreamStateNotification)) return false;
        if (!super.equals(o)) return false;

        SourcedStreamStateNotification that = (SourcedStreamStateNotification) o;

        if (source != null ? !source.equals(that.source) : that.source != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (source != null ? source.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SourcedStreamStateNotification{" +
                "source=" + source +
                "} " + super.toString();
    }

    public static <T> SourcedStreamStateNotification<T> bufferStartNotification(Interest<T> interest, Source source) {
        return new SourcedStreamStateNotification<>(BufferState.BufferStart, interest, source);
    }

    public static <T> SourcedStreamStateNotification<T> bufferEndNotification(Interest<T> interest, Source source) {
        return new SourcedStreamStateNotification<>(BufferState.BufferEnd, interest, source);
    }

}

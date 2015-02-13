package com.netflix.eureka2.interests;

import static com.netflix.eureka2.utils.Asserts.assertNonNull;

/**
 * Change notification type, used internally only, that carries information about
 * which atomic interest subscription the state notification belongs to.
 *
 * @author Tomasz Bak
 */
public class StreamStateNotification<T> extends ChangeNotification<T> {

    public enum BufferState {Unknown, BufferStart, BufferEnd}

    private final BufferState bufferState;
    private final Interest<T> interest;

    public StreamStateNotification(BufferState bufferState, Interest<T> interest) {
        super(Kind.BufferSentinel, null);

        assertNonNull(bufferState, "batchingState");
        assertNonNull(interest, "interest");

        this.bufferState = bufferState;
        this.interest = interest;
    }

    public BufferState getBufferState() {
        return bufferState;
    }

    public Interest<T> getInterest() {
        return interest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;

        StreamStateNotification that = (StreamStateNotification) o;

        if (bufferState != that.bufferState)
            return false;
        if (!interest.equals(that.interest))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + bufferState.hashCode();
        result = 31 * result + interest.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "StreamStateNotification{" +
                "batchingState=" + bufferState +
                ", interest=" + interest +
                '}';
    }

    public static <T> StreamStateNotification<T> bufferStartNotification(Interest<T> interest) {
        return new StreamStateNotification<>(BufferState.BufferStart, interest);
    }

    public static <T> StreamStateNotification<T> bufferEndNotification(Interest<T> interest) {
        return new StreamStateNotification<>(BufferState.BufferEnd, interest);
    }
}

package com.netflix.eureka2.interests;

import static com.netflix.eureka2.utils.Asserts.assertNonNull;

/**
 * Change notification type, used internally only, that carries information about
 * which atomic interest subscription the state notification belongs to.
 *
 * @author Tomasz Bak
 */
public class StreamStateNotification<T> extends ChangeNotification<T> {

    public enum BufferingState {Unknown, Buffer, FinishBuffering}

    private final BufferingState bufferingState;
    private final Interest<T> interest;

    public StreamStateNotification(BufferingState bufferingState, Interest<T> interest) {
        super(Kind.BufferingSentinel, null);

        assertNonNull(bufferingState, "batchingState");
        assertNonNull(interest, "interest");

        this.bufferingState = bufferingState;
        this.interest = interest;
    }

    public BufferingState getBufferingState() {
        return bufferingState;
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

        if (bufferingState != that.bufferingState)
            return false;
        if (!interest.equals(that.interest))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + bufferingState.hashCode();
        result = 31 * result + interest.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "StreamStateNotification{" +
                "batchingState=" + bufferingState +
                ", interest=" + interest +
                '}';
    }

    public static <T> StreamStateNotification<T> bufferNotification(Interest<T> interest) {
        return new StreamStateNotification<>(BufferingState.Buffer, interest);
    }

    public static <T> StreamStateNotification<T> finishBufferingNotification(Interest<T> interest) {
        return new StreamStateNotification<>(BufferingState.FinishBuffering, interest);
    }
}

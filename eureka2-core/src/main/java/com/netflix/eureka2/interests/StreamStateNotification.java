package com.netflix.eureka2.interests;

/**
 * Change notification type, used internally only, that carries information about
 * which atomic interest subscription the state notification belongs to.
 *
 * @author Tomasz Bak
 */
public class StreamStateNotification<T> extends ChangeNotification<T> {
    private final Interest<T> interest;

    protected StreamStateNotification(Kind kind, Interest<T> interest) {
        super(kind, null);
        this.interest = interest;
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

        if (interest != null ? !interest.equals(that.interest) : that.interest != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (interest != null ? interest.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "StreamStateNotification{" +
                "kind=" + getKind() +
                ", interest=" + interest +
                '}';
    }

    public static <T> StreamStateNotification<T> bufferNotification(Interest<T> interest) {
        return new StreamStateNotification<>(Kind.Buffer, interest);
    }

    public static <T> StreamStateNotification<T> finishBufferingNotification(Interest<T> interest) {
        return new StreamStateNotification<>(Kind.FinishBuffering, interest);
    }
}

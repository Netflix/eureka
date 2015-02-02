package com.netflix.eureka2.interests;

/**
 * This class describes current state of the notification stream. Its main
 * purpose now is to delineate between snapshot and live updates. In the
 * future it may carry over more information, like number of outstanding items
 * ready to be send in that stream.
 *
 * @author Tomasz Bak
 */
public class StreamState<T> {

    public enum Kind {Snapshot, Live}

    private final Kind kind;
    private final Interest<T> interest;

    protected StreamState() {
        kind = null;
        interest = null;
    }

    public StreamState(Kind kind, Interest<T> interest) {
        this.kind = kind;
        this.interest = interest;
    }

    public Kind getKind() {
        return kind;
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

        StreamState that = (StreamState) o;

        if (interest != null ? !interest.equals(that.interest) : that.interest != null)
            return false;
        if (kind != that.kind)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = kind != null ? kind.hashCode() : 0;
        result = 31 * result + (interest != null ? interest.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NotificationStreamState{" +
                "kind=" + kind +
                ", interest=" + interest +
                '}';
    }
}

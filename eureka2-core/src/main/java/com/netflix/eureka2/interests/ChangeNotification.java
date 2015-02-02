package com.netflix.eureka2.interests;

/**
 * A notification for a change in registry data.
 *
 * @author Nitesh Kant
 */
public class ChangeNotification<T> {

    public enum Kind {Add, Delete, Modify, StreamStatus}

    private final Kind kind;
    private final T data;
    private final StreamState<T> streamState;

    protected ChangeNotification(StreamState<T> streamState) {
        this.kind = Kind.StreamStatus;
        this.data = null;
        this.streamState = streamState;
    }

    public ChangeNotification(Kind kind, T data) {
        this(kind, data, null);
    }

    public ChangeNotification(Kind kind, T data, StreamState<T> streamState) {
        if (null == kind) {
            throw new NullPointerException("Notification kind can not be null.");
        }
        if (null == data) {
            throw new NullPointerException("Data can not be null.");
        }
        this.kind = kind;
        this.data = data;
        this.streamState = streamState;
    }

    public Kind getKind() {
        return kind;
    }

    public T getData() {
        return data;
    }

    public StreamState<T> getStreamState() {
        return streamState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ChangeNotification that = (ChangeNotification) o;

        if (data != null ? !data.equals(that.data) : that.data != null)
            return false;
        if (kind != that.kind)
            return false;
        if (streamState != null ? !streamState.equals(that.streamState) : that.streamState != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = kind != null ? kind.hashCode() : 0;
        result = 31 * result + (data != null ? data.hashCode() : 0);
        result = 31 * result + (streamState != null ? streamState.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChangeNotification{" +
                "kind=" + kind +
                ", data=" + data +
                ", streamState=" + streamState +
                '}';
    }
}

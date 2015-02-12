package com.netflix.eureka2.interests;

/**
 * A notification for a change in registry data.
 *
 * @author Nitesh Kant
 */
public class ChangeNotification<T> {

    public enum Kind {Add, Delete, Modify, BufferingSentinel}

    private final Kind kind;
    private final T data;

    public ChangeNotification(Kind kind, T data) {
        if (null == kind) {
            throw new NullPointerException("Notification kind can not be null.");
        }
        if (null == data && _isDataNotification(kind)) {
            throw new NullPointerException("Data can not be null.");
        }
        this.kind = kind;
        this.data = data;
    }

    public boolean isDataNotification() {
        return _isDataNotification(kind);
    }

    public Kind getKind() {
        return kind;
    }

    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ChangeNotification{" + "kind=" + kind + ", data=" + data + '}';
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

        return true;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + (data != null ? data.hashCode() : 0);
        return result;
    }

    private static boolean _isDataNotification(Kind kind) {
        return kind == Kind.Add || kind == Kind.Delete || kind == Kind.Modify;
    }
}

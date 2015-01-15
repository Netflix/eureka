package com.netflix.eureka2.interests;

/**
 * A notification for a change in registry data.
 *
 * @author Nitesh Kant
 */
public class ChangeNotification<T> {

    public enum Kind { Add, Delete, Modify }

    private final Kind kind;
    private final T data;

    public ChangeNotification(Kind kind, T data) {
        if (null == kind) {
            throw new NullPointerException("Notification kind can not be null.");
        }
        if (null == data) {
            throw new NullPointerException("Data can not be null.");
        }
        this.kind = kind;
        this.data = data;
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChangeNotification)) {
            return false;
        }

        ChangeNotification that = (ChangeNotification) o;

        return data.equals(that.data) && kind == that.kind;

    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + data.hashCode();
        return result;
    }
}

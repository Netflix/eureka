package com.netflix.eureka.interests;

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
}

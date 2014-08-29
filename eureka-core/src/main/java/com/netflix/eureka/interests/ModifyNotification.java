package com.netflix.eureka.interests;

/**
 * A specific {@link ChangeNotification} class for modifications. This adds a metadata about the change that was applied
 * to the existing value which returned in the value associated with the notification.
 *
 * This metadata is useful to optimize sending of the entire new data (typically over the wire) when only a few fields
 * have actually changed.
 *
 * @author Nitesh Kant
 */
public class ModifyNotification<T> extends ChangeNotification<T> {

    private final T prev;

    /**
     * Creates a new notification.
     *
     * @param data the latest version of the data
     * @param prev the prev version of the data
     */
    public ModifyNotification(T data, T prev) {
        super(Kind.Modify, data);
        this.prev = prev;
    }

    public T getPrev() {
        return prev;
    }
}

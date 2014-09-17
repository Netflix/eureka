package com.netflix.eureka.interests;

import com.netflix.eureka.registry.Delta;

import java.util.Set;

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

    private final Set<Delta<?>> delta;

    /**
     * Creates a new notification.
     *
     * @param data Data which resulted after applying the passed delta to the existing data.
     * @param delta Delta which resulted in the passed {@code data}. The collection should typically be immutable.
     */
    public ModifyNotification(T data, Set<Delta<?>> delta) {
        super(Kind.Modify, data);
        this.delta = delta;
    }

    public Set<Delta<?>> getDelta() {
        return delta;
    }

    @Override
    public String toString() {
        return "ModifyNotification{" +
                "delta=" + delta +
                "} " + super.toString();
    }
}

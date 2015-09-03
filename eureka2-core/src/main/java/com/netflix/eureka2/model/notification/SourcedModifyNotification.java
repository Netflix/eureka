package com.netflix.eureka2.model.notification;

import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;

import java.util.Set;

/**
 * @author David Liu
 */
public class SourcedModifyNotification<T> extends ModifyNotification<T> implements Sourced {
    private final Source source;

    public SourcedModifyNotification(T data, Set<Delta<?>> delta, Source source) {
        super(data, delta);
        this.source = source;
    }

    public ModifyNotification<T> toBaseNotification() {
        return new ModifyNotification<>(getData(), getDelta());
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "SourcedModifyNotification{" +
                "source=" + source +
                "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcedModifyNotification)) return false;
        if (!super.equals(o)) return false;

        SourcedModifyNotification that = (SourcedModifyNotification) o;

        if (source != null ? !source.equals(that.source) : that.source != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (source != null ? source.hashCode() : 0);
        return result;
    }
}

package com.netflix.eureka2.model.notification;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;

/**
 * @author David Liu
 */
public class SourcedChangeNotification<T> extends ChangeNotification<T> implements Sourced {
    private final Source source;

    public SourcedChangeNotification(ChangeNotification<T> notification, Source source) {
        this(notification.getKind(), notification.getData(), source);
    }

    public SourcedChangeNotification(Kind kind, T data, Source source) {
        super(kind, data);
        this.source = source;
    }

    public ChangeNotification<T> toBaseNotification() {
        return new ChangeNotification<>(getKind(), getData());
    }

    @Override
    public Source getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "SourcedChangeNotification{" +
                "source=" + source +
                "} " + super.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SourcedChangeNotification)) return false;
        if (!super.equals(o)) return false;

        SourcedChangeNotification that = (SourcedChangeNotification) o;

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

package com.netflix.eureka2.server.registry;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.registry.EurekaServerRegistry.Status;
import rx.Observable;

/**
 * A holder object that maintains copies of the same data (as defined by some metric, such as id) that are
 * from different sources. Updates/removes to this holder are only done to the copy from the corresponding source.
 *
 * The holder should maintain a consistent view (w.r.t. to it's view, but not necessarily to a particular copy)
 * of this data to return.
 *
 * @param <V> the data type
 *
 * @author David Liu
 */
public interface MultiSourcedDataHolder<V> {

    /**
     * @return the uuid that is common to all the data copies
     */
    String getId();

    /**
     * @return the number of copies of data currently in this holder
     */
    int size();

    /**
     * @return the view copy of the data, if exists
     */
    V get();

    /**
     * @return the copy of data for the given source, if exists
     */
    V get(Source source);

    /**
     * @return the source of the view copy of the data, if exists
     */
    Source getSource();

    /**
     * @return the view copy of the data as a change notification, if exists
     */
    ChangeNotification<V> getChangeNotification();

    /**
     * @param source the source to update
     * @param data the data copy
     */
    Observable<Status> update(Source source, V data);

    /**
     * @param source the source to delete
     */
    Observable<Status> remove(Source source, V data);


    final class Snapshot<V> {
        protected final Source source;
        protected final V data;
        protected final ChangeNotification<V> notification;

        protected Snapshot(Source source, V data) {
            this.source = source;
            this.data = data;
            this.notification = new ChangeNotification<>(ChangeNotification.Kind.Add, data);
        }
    }


    /**
     * An interface for an accessor to the data store that holds the multiSourcedDataHolder
     */
    interface HolderStoreAccessor<V> {
        void add(MultiSourcedDataHolder<V> holder);
        MultiSourcedDataHolder<V> get(String id);
        void remove(String id);
        boolean contains(String id);
    }

}

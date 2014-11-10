package com.netflix.rx.eureka.data;

import com.netflix.rx.eureka.interests.ChangeNotification;
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
     * @return the view of the data
     */
    ChangeNotification<V> getSnapshot();

    /**
     * @return the view of the data if it matches the specified source
     */
    ChangeNotification<V> getSnapshotIfMatch(Source source);

    /**
     * @return the number of copies of data currently in this holder
     */
    int numCopies();

    /**
     * @return the copy of data for the given source, if exists
     */
    V getCopyForSource(Source source);

    /**
     * @param source the source to update
     * @param data the data copy
     */
    Observable<Void> update(Source source, V data);

    /**
     * @param source the source to delete
     */
    Observable<Void> remove(Source source);


    static final class Snapshot<V> {
        protected final Source source;
        protected final V data;
        protected final ChangeNotification<V> notification;

        protected Snapshot(Source source, V data) {
            this.source = source;
            this.data = data;
            this.notification = new ChangeNotification<>(ChangeNotification.Kind.Add, data);
        }
    }

}

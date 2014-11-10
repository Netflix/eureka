package com.netflix.rx.eureka.data;

import com.netflix.rx.eureka.datastore.NotificationsSubject;
import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.ModifyNotification;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.utils.SerializedTaskInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This holder maintains the data copies in a list ordered by write time. It also maintains a consistent "snapshot"
 * view of the copies, sourced from the head of the ordered list of copies. This snapshot is updated w.r.t. the
 * previous snapshot for each write into this holder, if the write it to the head copy.
 *
 * This holder serializes actions between update and remove by queuing (via SerializedTaskInvoker)
 * TODO think about a more efficient way of dealing with the concurrency here without needing to lock
 *
 * @author David Liu
 */
public class NotifyingInstanceInfoHolder extends SerializedTaskInvoker implements MultiSourcedDataHolder<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(NotifyingInstanceInfoHolder.class);

    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry

    private final LinkedHashMap<Source, InstanceInfo> dataMap;  // for order
    private final String id;
    private Snapshot<InstanceInfo> snapshot;

    public NotifyingInstanceInfoHolder(NotificationsSubject<InstanceInfo> notificationSubject, String id) {
        this.notificationSubject = notificationSubject;
        this.id = id;
        this.dataMap = new LinkedHashMap<>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int numCopies() {
        return dataMap.size();
    }

    @Override
    public ChangeNotification<InstanceInfo> getSnapshot() {
        if (snapshot != null) {
            return snapshot.notification;
        }
        return null;
    }

    @Override
    public ChangeNotification<InstanceInfo> getSnapshotIfMatch(Source source) {
        if (snapshot != null && snapshot.source.equals(source)) {
            return snapshot.notification;
        }
        return null;
    }

    @Override
    public InstanceInfo getCopyForSource(Source source) {
        return dataMap.get(source);
    }

    /**
     * if the update is an add at the head, send an ADD notification of the data;
     * if the update is an add to an existing head, send the diff as a MODIFY notification;
     * else no-op.
     */
    @Override
    public Observable<Void> update(final Source source, final InstanceInfo data) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                InstanceInfo prev = dataMap.put(source, data);
                //TODO take itself out of the expiry queue

                Snapshot<InstanceInfo> currSnapshot = snapshot;
                Snapshot<InstanceInfo> newSnapshot = new Snapshot<>(source, data);
                if (currSnapshot == null) {  // real add to the head
                    snapshot = newSnapshot;
                    notificationSubject.onNext(newSnapshot.notification);
                } else if (currSnapshot.source.equals(newSnapshot.source)) {  // modify to current snapshot
                    snapshot = newSnapshot;
                    ChangeNotification<InstanceInfo> modifyNotification
                            = new ModifyNotification<>(newSnapshot.data, newSnapshot.data.diffOlder(currSnapshot.data));
                    notificationSubject.onNext(modifyNotification);
                } else {  // different source, no-op
                    logger.debug("Different source from current snapshot, not updating");
                }
                return Observable.empty();
            }
        }).doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                logger.error("Error updating instance copy");
            }
        });
    }

    /**
     * If the remove is from the head and there is a new head, send the diff to the old head as a MODIFY notification;
     * if the remove is of the last copy, send a DELETE notification;
     * else no-op.
     */
    @Override
    public Observable<Void> remove(final Source source) {
        return submitForAck(new Callable<Observable<Void>>() {
            @Override
            public Observable<Void> call() throws Exception {
                InstanceInfo removed = dataMap.remove(source);

                Snapshot<InstanceInfo> currSnapshot = snapshot;
                if (removed == null) {  // nothing removed, no-op
                    logger.debug("source:data does not exist, no-op");
                } else if (source.equals(currSnapshot.source)) {  // remove of current snapshot
                    Map.Entry<Source, InstanceInfo> newHead = dataMap.isEmpty() ? null : dataMap.entrySet().iterator().next();
                    if (newHead == null) {  // removed last copy
                        snapshot = null;
                        ChangeNotification<InstanceInfo> deleteNotification
                                = new ChangeNotification<>(ChangeNotification.Kind.Delete, removed);
                        notificationSubject.onNext(deleteNotification);
                        logger.debug("Removed last copy from holder, adding holder to expiry queue");
                        // TODO add to expiry queue, this should not be blocking
                    } else {  // promote the newHead as the snapshot and publish a modify notification
                        Snapshot<InstanceInfo> newSnapshot = new Snapshot<>(newHead.getKey(), newHead.getValue());
                        snapshot = newSnapshot;
                        ChangeNotification<InstanceInfo> modifyNotification
                                = new ModifyNotification<>(newSnapshot.data, newSnapshot.data.diffOlder(currSnapshot.data));
                        notificationSubject.onNext(modifyNotification);
                    }
                } else {  // remove of copy that's not the source of the snapshot, no-op
                    logger.debug("removed non-head copy of source:data");
                }
                return Observable.empty();
            }
        }).doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                logger.error("Error removing instance copy");
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotifyingInstanceInfoHolder)) return false;

        NotifyingInstanceInfoHolder that = (NotifyingInstanceInfoHolder) o;

        if (!dataMap.equals(that.dataMap)) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (notificationSubject != null ? !notificationSubject.equals(that.notificationSubject) : that.notificationSubject != null)
            return false;
        if (snapshot != null ? !snapshot.equals(that.snapshot) : that.snapshot != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = notificationSubject != null ? notificationSubject.hashCode() : 0;
        result = 31 * result + (dataMap.hashCode());
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (snapshot != null ? snapshot.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "NotifyingInstanceInfoHolder{" +
                "notificationSubject=" + notificationSubject +
                ", dataMap=" + dataMap +
                ", id='" + id + '\'' +
                ", snapshot=" + snapshot +
                "} " + super.toString();
    }
}

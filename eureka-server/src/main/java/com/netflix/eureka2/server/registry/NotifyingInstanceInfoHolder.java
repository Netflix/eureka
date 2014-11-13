package com.netflix.eureka2.server.registry;

import com.netflix.eureka2.datastore.NotificationsSubject;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaServerRegistry.Status;
import com.netflix.eureka2.utils.SerializedTaskInvoker;
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
 * previous snapshot for each write into this holder, if the write is to the head copy. When the head copy is removed,
 * the next copy in line is promoted as the new view.
 *
 * This holder serializes actions between update and remove by queuing (via SerializedTaskInvoker)
 * TODO think about a more efficient way of dealing with the concurrency here without needing to lock
 *
 * @author David Liu
 */
public class NotifyingInstanceInfoHolder implements MultiSourcedDataHolder<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(NotifyingInstanceInfoHolder.class);

    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry

    private final LinkedHashMap<Source, InstanceInfo> dataMap;  // for order
    private final NotificationTaskInvoker invoker;
    private final String id;
    private Snapshot<InstanceInfo> snapshot;

    public NotifyingInstanceInfoHolder(NotificationsSubject<InstanceInfo> notificationSubject, NotificationTaskInvoker invoker, String id) {
        this.notificationSubject = notificationSubject;
        this.invoker = invoker;
        this.id = id;
        this.dataMap = new LinkedHashMap<>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int size() {
        return dataMap.size();
    }

    @Override
    public InstanceInfo get() {
        if (snapshot != null) {
            return snapshot.data;
        }
        return null;
    }

    @Override
    public InstanceInfo get(Source source) {
        return dataMap.get(source);
    }

    @Override
    public Source getSource() {
        if (snapshot != null) {
            return snapshot.source;
        }
        return null;
    }

    @Override
    public ChangeNotification<InstanceInfo> getChangeNotification() {
        if (snapshot != null) {
            return snapshot.notification;
        }
        return null;
    }

    /**
     * if the update is an add at the head, send an ADD notification of the data;
     * if the update is an add to an existing head, send the diff as a MODIFY notification;
     * else no-op.
     */
    @Override
    public Observable<Status> update(final Source source, final InstanceInfo data) {
        return invoker.submitTask(new Callable<Observable<Status>>() {
            @Override
            public Observable<Status> call() throws Exception {
                // Do not overwrite with older version
                // This should never happen for adds coming from the channel, as they
                // are always processed in order (unlike remove which has eviction queue).
                InstanceInfo prev = dataMap.get(source);
                if (prev != null && prev.getVersion() > data.getVersion()) {
                    return Observable.just(Status.AddExpired);
                }

                dataMap.put(source, data);

                Snapshot<InstanceInfo> currSnapshot = snapshot;
                Snapshot<InstanceInfo> newSnapshot = new Snapshot<>(source, data);
                Status result = Status.AddedChange;

                if (currSnapshot == null) {  // real add to the head
                    snapshot = newSnapshot;
                    notificationSubject.onNext(newSnapshot.notification);
                    result = Status.AddedFirst;
                } else {
                    if (currSnapshot.source.equals(newSnapshot.source)) {  // modify to current snapshot
                        snapshot = newSnapshot;
                        ChangeNotification<InstanceInfo> modifyNotification
                                = new ModifyNotification<>(newSnapshot.data, newSnapshot.data.diffOlder(currSnapshot.data));
                        notificationSubject.onNext(modifyNotification);
                    } else {  // different source, no-op
                        logger.debug("Different source from current snapshot, not updating");
                    }
                }
                return Observable.just(result);
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
    public Observable<Status> remove(final Source source, final InstanceInfo data) {
        return invoker.submitTask(new Callable<Observable<Status>>() {
            @Override
            public Observable<Status> call() throws Exception {
                // Do not remove older version.
                // Older version may come from eviction queue, after registration from
                // new connection was already processed.
                InstanceInfo prev = dataMap.get(source);
                if (prev != null && prev.getVersion() > data.getVersion()) {
                    return Observable.just(Status.RemoveExpired);
                }

                InstanceInfo removed = dataMap.remove(source);
                Snapshot<InstanceInfo> currSnapshot = snapshot;
                Status result = Status.RemovedFragment;

                if (removed == null) {  // nothing removed, no-op
                    logger.debug("source:data does not exist, no-op");
                    result = Status.RemoveExpired;
                } else if (source.equals(currSnapshot.source)) {  // remove of current snapshot
                    Map.Entry<Source, InstanceInfo> newHead = dataMap.isEmpty() ? null : dataMap.entrySet().iterator().next();
                    if (newHead == null) {  // removed last copy
                        snapshot = null;
                        ChangeNotification<InstanceInfo> deleteNotification
                                = new ChangeNotification<>(ChangeNotification.Kind.Delete, removed);
                        notificationSubject.onNext(deleteNotification);
                        logger.debug("Removed last copy from holder, adding holder to expiry queue");
                        result = Status.RemovedLast;
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
                return Observable.just(result);
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof NotifyingInstanceInfoHolder)) {
            return false;
        }

        NotifyingInstanceInfoHolder that = (NotifyingInstanceInfoHolder) o;

        if (!dataMap.equals(that.dataMap)) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (notificationSubject != null ? !notificationSubject.equals(that.notificationSubject) : that.notificationSubject != null) {
            return false;
        }
        if (snapshot != null ? !snapshot.equals(that.snapshot) : that.snapshot != null) {
            return false;
        }

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

    static class NotificationTaskInvoker extends SerializedTaskInvoker {
        Observable<Status> submitTask(Callable<Observable<Status>> task) {
            return submitForResult(task);
        }
    }
}

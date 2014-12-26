package com.netflix.eureka2.registry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.NotificationsSubject;
import com.netflix.eureka2.metric.SerializedTaskInvokerMetrics;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.interests.SourcedChangeNotification;
import com.netflix.eureka2.interests.SourcedModifyNotification;
import com.netflix.eureka2.registry.SourcedEurekaRegistry.Status;
import com.netflix.eureka2.utils.SerializedTaskInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action1;

/**
 * This holder maintains the data copies in a list ordered by write time. It also maintains a consistent "snapshot"
 * view of the copies, sourced from the head of the ordered list of copies. This snapshot is updated w.r.t. the
 * previous snapshot for each write into this holder, if the write is to the head copy. When the head copy is removed,
 * the next copy in line is promoted as the new view.
 *
 * This holder serializes actions between update and remove by queuing (via SerializedTaskInvoker)
 *
 * @author David Liu
 */
public class NotifyingInstanceInfoHolder implements MultiSourcedDataHolder<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(NotifyingInstanceInfoHolder.class);

    private final NotificationsSubject<InstanceInfo> notificationSubject;  // subject for all changes in the registry

    private final HolderStoreAccessor<NotifyingInstanceInfoHolder> holderStoreAccessor;
    private final LinkedHashMap<Source, InstanceInfo> dataMap;  // for order
    private final NotificationTaskInvoker invoker;
    private final String id;
    private Snapshot<InstanceInfo> snapshot;

    public NotifyingInstanceInfoHolder(
            HolderStoreAccessor<NotifyingInstanceInfoHolder> holderStoreAccessor,
            NotificationsSubject<InstanceInfo> notificationSubject,
            NotificationTaskInvoker invoker,
            String id)
    {
        this.holderStoreAccessor = holderStoreAccessor;
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
            return snapshot.getData();
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
            return snapshot.getSource();
        }
        return null;
    }

    @Override
    public SourcedChangeNotification<InstanceInfo> getChangeNotification() {
        if (snapshot != null) {
            return snapshot.getNotification();
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
                return doUpdate(source, data);
            }
        }).doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                logger.error("Error updating instance copy");
            }
        });
    }

    protected Observable<Status> doUpdate(final Source source, final InstanceInfo data) {
        // add self to the holder datastore if not already there, else delegate to existing one
        NotifyingInstanceInfoHolder existing = holderStoreAccessor.get(id);
        if (existing == null) {
            holderStoreAccessor.add(NotifyingInstanceInfoHolder.this);
        } else if (existing != NotifyingInstanceInfoHolder.this) {
            return existing.doUpdate(source, data);  // execute inline instead of reschedule as task
        }

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
            notificationSubject.onNext(newSnapshot.getNotification());
            result = Status.AddedFirst;
        } else {
            if (currSnapshot.getSource().equals(newSnapshot.getSource())) {  // modify to current snapshot
                snapshot = newSnapshot;

                Set<Delta<?>> delta = newSnapshot.getData().diffOlder(currSnapshot.getData());
                if (!delta.isEmpty()) {
                    ChangeNotification<InstanceInfo> modifyNotification
                            = new SourcedModifyNotification<>(newSnapshot.getData(), delta, newSnapshot.getSource());
                    notificationSubject.onNext(modifyNotification);
                } else {
                    logger.debug("No-change update for {}#{}", currSnapshot.getSource(), currSnapshot.getData().getId());
                }
            } else {  // different source, no-op
                logger.debug(
                        "Different source from current snapshot, not updating (head={}, received={})",
                        currSnapshot.getSource(), newSnapshot.getSource()
                );
            }
        }

        return Observable.just(result);
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
                } else if (source.equals(currSnapshot.getSource())) {  // remove of current snapshot
                    Map.Entry<Source, InstanceInfo> newHead = dataMap.isEmpty() ? null : dataMap.entrySet().iterator().next();
                    if (newHead == null) {  // removed last copy
                        snapshot = null;
                        ChangeNotification<InstanceInfo> deleteNotification
                                = new SourcedChangeNotification<>(ChangeNotification.Kind.Delete, removed, source);
                        notificationSubject.onNext(deleteNotification);

                        // remove self from the holder datastore if empty
                        holderStoreAccessor.remove(id);
                        result = Status.RemovedLast;
                    } else {  // promote the newHead as the snapshot and publish a modify notification
                        Snapshot<InstanceInfo> newSnapshot = new Snapshot<>(newHead.getKey(), newHead.getValue());
                        snapshot = newSnapshot;

                        Set<Delta<?>> delta = newSnapshot.getData().diffOlder(currSnapshot.getData());
                        if (!delta.isEmpty()) {
                            ChangeNotification<InstanceInfo> modifyNotification
                                    = new SourcedModifyNotification<>(newSnapshot.getData(), delta, newSnapshot.getSource());
                            notificationSubject.onNext(modifyNotification);
                        } else {
                            logger.debug("No-change update for {}#{}", currSnapshot.getSource(), currSnapshot.getData().getId());
                        }
                    }
                } else {  // remove of copy that's not the source of the snapshot, no-op
                    logger.debug("removed non-head (head={}, received={})", currSnapshot.getSource(), source);
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
    public String toString() {
        return "NotifyingInstanceInfoHolder{" +
                "notificationSubject=" + notificationSubject +
                ", dataMap=" + dataMap +
                ", id='" + id + '\'' +
                ", snapshot=" + snapshot +
                "} " + super.toString();
    }

    static class NotificationTaskInvoker extends SerializedTaskInvoker {

        NotificationTaskInvoker(SerializedTaskInvokerMetrics metrics, Scheduler scheduler) {
            super(metrics, scheduler);
        }

        Observable<Status> submitTask(Callable<Observable<Status>> task) {
            return submitForResult(task);
        }
    }
}

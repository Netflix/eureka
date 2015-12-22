package com.netflix.eureka2.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.internal.util.InstanceUtil;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.SourcedModifyNotification;
import com.netflix.eureka2.utils.functions.ChangeNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This holder maintains the data copies in a list ordered by write time. It also maintains a consistent "snapshot"
 * view of the copies, sourced from the head of the ordered list of copies.
 *
 * This snapshot is updated w.r.t. the previous snapshot for each write into this holder, if the write is to
 * the head copy. When the head copy is removed, the next copy in line is promoted as the new view.
 *
 * If the current snapshot is from a non-local source and an update from a local source is received, this new local
 * sourced update is promoted to the snapshot regardless the write order.
 *
 * @author David Liu
 */
public class MultiSourcedInstanceInfoHolder implements MultiSourcedDataHolder<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(MultiSourcedInstanceInfoHolder.class);

    private final HolderStore holderStore;
    private final String id;
    private final EurekaRegistryMetrics metrics;

    private Snapshot<InstanceInfo> snapshot;

    public MultiSourcedInstanceInfoHolder(String id, EurekaRegistryMetrics metrics) {
        this.id = id;
        this.metrics = metrics;
        this.holderStore = new HolderStore();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int size() {
        return holderStore.size();
    }

    @Override
    public boolean isEmpty() {
        return holderStore.isEmpty();
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
        return holderStore.getExact(source);
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

    @Override
    public Collection<Source> getAllSources() {
        return holderStore.getAllSources();
    }

    /**
     * - if the update is an add at the head, send an ADD notification of the data;
     * - if the update is an add to an existing head, send the diff as a MODIFY notification;
     * - if the update is from a local source type and the current snapshot is from a non-local source type,
     *   promote the new update to the snapshot and generate notification regardless of whether they diff;
     * - else no-op.
     */
    @SuppressWarnings("unchecked")
    @Override
    public ChangeNotification<InstanceInfo>[] update(final Source source, final InstanceInfo data) {
        if (holderStore.getMatching(source) == null) {
            metrics.incrementRegistrationCounter(source.getOrigin());
        }
        holderStore.put(source, data);

        Snapshot<InstanceInfo> currSnapshot = snapshot;
        Snapshot<InstanceInfo> newSnapshot = new Snapshot<>(source, data);

        if (currSnapshot == null) {  // real add to the head
            snapshot = newSnapshot;
            return new SourcedChangeNotification[]{newSnapshot.getNotification()};
        } else if ((currSnapshot.getSource().getOrigin() != Source.Origin.LOCAL) &&
                (source.getOrigin() == Source.Origin.LOCAL)) {  // promote new update from local to snapshot
            snapshot = newSnapshot;
            return new SourcedChangeNotification[]{newSnapshot.getNotification()};
        } else {
            if (matches(currSnapshot.getSource(), newSnapshot.getSource())) {  // modify to current snapshot
                snapshot = newSnapshot;

                Set<Delta<?>> delta = (newSnapshot.getData()).diffOlder(currSnapshot.getData());
                if (!delta.isEmpty()) {
                    SourcedModifyNotification<InstanceInfo> modifyNotification
                            = new SourcedModifyNotification<>(newSnapshot.getData(), delta, newSnapshot.getSource());
                    return new SourcedModifyNotification[]{modifyNotification};
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

        logger.debug("Updated instance {} for source {}", data.getId(), source);
        return ChangeNotifications.emptyChangeNotifications();
    }

    /**
     * - If the remove is from the head and there is a new head, send the diff to the old head as a MODIFY notification;
     * - if the remove is of the last copy, send a DELETE notification;
     * - If the moreve is from the head and the removed is of LOCAL source but the promoted is not, generate a Delete
     *   notification with LOCAL source followed by an Add notification with the new source.
     * - else no-op.
     */
    @SuppressWarnings("unchecked")
    @Override
    public ChangeNotification<InstanceInfo>[] remove(final Source source) {
        InstanceInfo removed = holderStore.remove(source);
        Snapshot<InstanceInfo> currSnapshot = snapshot;

        if (removed == null) {  // nothing removed, no-op
            logger.debug("source:data does not exist, no-op");
        } else {
            metrics.incrementUnregistrationCounter(source.getOrigin());

            if (matches(source, currSnapshot.getSource())) {  // remove of current snapshot
                Map.Entry<Source, InstanceInfo> newHead = holderStore.nextEntry();
                if (newHead == null) {  // removed last copy
                    snapshot = null;
                    ChangeNotification<InstanceInfo> deleteNotification
                            = new SourcedChangeNotification<>(ChangeNotification.Kind.Delete, removed, source);

                    return new ChangeNotification[]{deleteNotification};
                } else {  // promote the newHead as the snapshot and publish a modify notification
                    Snapshot<InstanceInfo> newSnapshot = new Snapshot<>(newHead.getKey(), newHead.getValue());
                    snapshot = newSnapshot;

                    if (source.getOrigin() == Source.Origin.LOCAL && snapshot.getSource().getOrigin() != Source.Origin.LOCAL) {
                        ChangeNotification<InstanceInfo> deleteNotification
                                = new SourcedChangeNotification<>(ChangeNotification.Kind.Delete, removed, source);

                        ChangeNotification<InstanceInfo> addNotification
                                = new SourcedChangeNotification<>(ChangeNotification.Kind.Add, snapshot.getData(), snapshot.getSource());

                        return new ChangeNotification[]{deleteNotification, addNotification};
                    } else {
                        Set<Delta<?>> delta = (newSnapshot.getData()).diffOlder(currSnapshot.getData());
                        if (!delta.isEmpty()) {
                            ChangeNotification<InstanceInfo> modifyNotification
                                    = new SourcedModifyNotification<>(newSnapshot.getData(), delta, newSnapshot.getSource());
                            return new ChangeNotification[]{modifyNotification};
                        } else {
                            logger.debug("No-change update for {}#{}", currSnapshot.getSource(), currSnapshot.getData().getId());
                        }
                    }
                }
                logger.debug("Removed head of instance {} for source {}", removed.getId(), source);
            } else {  // remove of copy that's not the source of the snapshot, no-op
                logger.debug("Removed non-head of instance {} for source {}", removed.getId(), source);
            }
        }
        return ChangeNotifications.emptyChangeNotifications();
    }

    @Override
    public String toString() {
        return "NotifyingInstanceInfoHolder{" +
                ", id='" + id + '\'' +
                ", snapshot=" + snapshot +
                ", dataStore=" + holderStore +
                '}';
    }

    public String toStringSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("NotifyingInstanceInfoHolder{");
        sb.append(", id='").append(id).append('\'');

        sb.append(", snapshot=");
        if (snapshot == null) {
            sb.append("null");
        } else {
            sb.append("{data=").append(InstanceUtil.toStringSummary(snapshot.getData()))
                    .append(", source=").append(snapshot.getSource()).append('}');
        }

        sb.append(", dataStore={size=").append(holderStore.size()).append('}');
        sb.append('}');

        return sb.toString();
    }

    /**
     * @return true if the two sources have the same origin and name. Don't use a matcher from source
     *         to avoid the object creation.
     */
    private boolean matches(Source one, Source two) {
        if (one != null && two != null) {
            boolean originMatches = (one.getOrigin() == two.getOrigin());
            boolean nameMatches = (one.getName() == null)
                    ? (two.getName() == null)
                    : one.getName().equals(two.getName());
            return originMatches && nameMatches;
        } else {
            return one == null && two == null;
        }
    }

    /**
     * Assume access to this is synchronized.
     * All access to the dataStore first route through the sourceMap for the authoritative source to use
     */
    /* visible for testing */ static class HolderStore {
        protected final Map<String, Source> sourceMap = new HashMap<>();
        protected final LinkedHashMap<Source, InstanceInfo> dataMap = new LinkedHashMap<>();  // for ordering

        /**
         * Matches sources on origin:name only.
         * - If a matching source already exist in the sourceMap, remove from the dataStore first before add (as the
         *   curr source may not equal due to a different source id).
         * - Otherwise, just add to the dataStore
         * - finally, add the source and sourceKey to the sourceMap
         */
        public void put(Source source, InstanceInfo instanceInfo) {
            String sourceKey = sourceKey(source);
            Source currIfExist = sourceMap.get(sourceKey);
            if (currIfExist != null) {
                dataMap.remove(currIfExist);
            }

            dataMap.put(source, instanceInfo);
            sourceMap.put(sourceKey, source);
        }

        public Collection<Source> getAllSources() {
            return sourceMap.values();
        }

        public InstanceInfo getMatching(Source source) {
            Source currIfExist = getMatchingSource(source);
            if (currIfExist != null) {
                return dataMap.get(currIfExist);
            }
            return null;
        }

        public InstanceInfo getExact(Source source) {
            return dataMap.get(source);
        }

        /**
         * Matches sources on origin:name:id.
         * - If a matching source already exist in the sourceMap, and has the same id as the input source, do removal
         * - If a matching source already exist in the sourceMap, but have a different id as the input source, no-op
         * - Otherwise, no-op
         */
        public InstanceInfo remove(Source source) {
            String sourceKey = sourceKey(source);
            Source currIfExist = sourceMap.get(sourceKey);
            if (currIfExist != null) {
                if (currIfExist.getId() == source.getId()) {
                    sourceMap.remove(sourceKey);
                    return dataMap.remove(currIfExist);
                } else {  // no-op
                    return null;
                }
            } else {  // no-op
                return null;
            }
        }

        public int size() {
            return dataMap.size();
        }

        public boolean isEmpty() {
            return dataMap.isEmpty();
        }

        public Map.Entry<Source, InstanceInfo> nextEntry() {
            if (dataMap.isEmpty()) {
                return null;
            }
            return dataMap.entrySet().iterator().next();
        }

        /**
         * @return the current stored source that matches the given source's origin and name (but not necessarily id).
         */
        private Source getMatchingSource(Source source) {
            String key = sourceKey(source);
            return sourceMap.get(key);
        }

        /**
         * @return the matching key for sources, where the sources are matched on origin:name only.
         */
        private String sourceKey(Source source) {
            return source.getOriginNamePair();
        }
    }
}

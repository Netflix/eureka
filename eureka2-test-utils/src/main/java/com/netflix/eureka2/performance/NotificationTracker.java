package com.netflix.eureka2.performance;

/**
 * @author Tomasz Bak
 */

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.data.toplogy.TopologyFunctions;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action1;

import static com.netflix.eureka2.data.toplogy.TopologyFunctions.getTimestamp;

/**
 * Tracks expected interest subscription notifications for an actor.
 *
 * @author Tomasz Bak
 */
class NotificationTracker {

    private static final Logger logger = LoggerFactory.getLogger(NotificationTracker.class);

    private final long startTime;
    private final int latencyThreshold;
    private final PerformanceScoreBoard scoreBoard;

    private final Scheduler scheduler;
    private final PriorityBlockingQueue<Holder> expiryQueue = new PriorityBlockingQueue<>(256, HOLDER_TIMESTAMP_COMPARATOR);
    private final ConcurrentMap<String, List<Holder>> expectedNotificationsById = new ConcurrentHashMap<>();
    private final Subscription latentTrackerSubscription;

    NotificationTracker(int latencyThreshold,
                        PerformanceScoreBoard scoreBoard,
                        Scheduler scheduler) {
        this.startTime = scheduler.now();
        this.latencyThreshold = latencyThreshold;
        this.scoreBoard = scoreBoard;
        this.scheduler = scheduler;
        latentTrackerSubscription = Observable.timer(1, 1, TimeUnit.SECONDS)
                .doOnNext(new Action1<Long>() {
                              @Override
                              public void call(Long tick) {
                                  findLatent();
                              }
                          }
                )
                .subscribe();
    }

    /**
     * This method is not executed concurrently, but it may run parallel with
     * {@link #verifyWithExpectations(ChangeNotification)} that modifies the same data structures.
     */
    void addExpectation(ChangeNotification<InstanceInfo> expectedNotification) {
        logger.debug("Adding instance {} to track", expectedNotification.getData().getId());
        if (getTimestamp(expectedNotification) == null) {
            throw new IllegalStateException("Expected notification added with no timestamp");
        }
        Holder holder = new Holder(expectedNotification);
        expiryQueue.add(holder);
        String id = expectedNotification.getData().getId();
        List<Holder> holders = expectedNotificationsById.get(id);
        if (holders == null) {
            holders = new ArrayList<>();
            expectedNotificationsById.put(id, holders);
        }
        synchronized (holders) {
            holders.add(holder);
        }
    }

    /**
     * This method is not executed concurrently, but it may run parallel with
     * {@link #addExpectation(ChangeNotification)} that modifies the same data structures.
     */
    void verifyWithExpectations(ChangeNotification<InstanceInfo> notification) {
        String id = notification.getData().getId();

        Long timestamp = getTimestamp(notification);
        if (timestamp == null) {
            logger.warn("Received notification with id={} without timestamp tag", id);
            return;
        }
        if (timestamp <= startTime) { // Notification before this tracker was created
            return;
        }

        scoreBoard.notificationIncrement();

        List<Holder> holders = expectedNotificationsById.get(id);
        if (holders != null) {
            synchronized (holders) {
                for (int i = 0; i < holders.size(); i++) {
                    Holder holder = holders.get(i);
                    if (isMatching(notification, holder)) {
                        holders.remove(i);
                        holder.markDelivered();
                        long latency = scheduler.now() - holder.getTimestamp();
                        scoreBoard.recordNotificationLatency(latency);
                        logger.debug("Recording latency for instance {}={}", id, latency);
                        return;
                    }
                }
            }
        }
        logger.warn("Ignoring unmatched {} notification for instance {}", notification.getKind(), id);
    }

    void stop() {
        latentTrackerSubscription.unsubscribe();
        long left = 0;
        for (List<Holder> holderList : expectedNotificationsById.values()) {
            left += holderList.size();
            for (Holder holder : holderList) {
                logger.debug("Releasing instance {}", System.identityHashCode(holder.getNotification().getData()));
            }
        }
        logger.info("Left {} notifications when stopped", left);
    }


    private static boolean isMatching(ChangeNotification<InstanceInfo> notification, Holder holder) {
        Long expectedTimestamp = getTimestamp(holder.getNotification());
        Long incomingTimestamp = getTimestamp(notification);
        if (incomingTimestamp == null) {
            return false;
        }
        return notification.getKind() == holder.getNotification().getKind() && expectedTimestamp.equals(incomingTimestamp);
    }

    private void findLatent() {
        long now = scheduler.now();
        for (Holder next = expiryQueue.peek(); next != null; next = expiryQueue.peek()) {
            if (next.isDelivered()) {
                expiryQueue.poll();
            } else if (next.getExpiryTime() <= now) {
                ChangeNotification<InstanceInfo> notification = next.getNotification();
                logger.warn("Latent {} notification with id {} by {}ms", notification.getKind(), notification.getData().getId(), next.getLatency());
                expiryQueue.poll();
                scoreBoard.latentNotificationIncrement();
            } else {
                break;
            }
        }
    }

    class Holder {
        private final ChangeNotification<InstanceInfo> notification;
        private final long timestamp;
        private final long startTime;

        private volatile boolean delivered;

        Holder(ChangeNotification<InstanceInfo> notification) {
            this.notification = notification;
            this.timestamp = TopologyFunctions.getTimestamp(notification);
            this.startTime = notification.getKind() == Kind.Add ? timestamp : scheduler.now();
        }

        public long getExpiryTime() {
            return startTime + latencyThreshold;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getLatency() {
            return scheduler.now() - startTime;
        }

        public ChangeNotification<InstanceInfo> getNotification() {
            return notification;
        }

        public boolean isDelivered() {
            return delivered;
        }

        public void markDelivered() {
            delivered = true;
        }
    }

    static final Comparator<Holder> HOLDER_TIMESTAMP_COMPARATOR = new Comparator<Holder>() {
        @Override
        public int compare(Holder o1, Holder o2) {
            return Long.compare(o1.getTimestamp(), o2.getTimestamp());
        }
    };
}
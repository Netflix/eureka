package com.netflix.eureka.util.batcher;

import com.netflix.discovery.util.SpectatorUtil;
import com.netflix.spectator.api.Spectator;
import com.netflix.spectator.api.histogram.PercentileDistributionSummary;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.eureka.util.batcher.TaskProcessor.ProcessingResult;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.eureka.Names.METRIC_REPLICATION_PREFIX;

/**
 * An active object with an internal thread accepting tasks from clients, and dispatching them to
 * workers in a pull based manner. Workers explicitly request an item or a batch of items whenever they are
 * available. This guarantees that data to be processed are always up to date, and no stale data processing is done.
 *
 * <h3>Task identification</h3>
 * Each task passed for processing has a corresponding task id. This id is used to remove duplicates (replace
 * older copies with newer ones).
 *
 * <h3>Re-processing</h3>
 * If data processing by a worker failed, and the failure is transient in nature, the worker will put back the
 * task(s) back to the {@link AcceptorExecutor}. This data will be merged with current workload, possibly discarded if
 * a newer version has been already received.
 *
 * @author Tomasz Bak
 */
class AcceptorExecutor<ID, T> {

    private static final Logger logger = LoggerFactory.getLogger(AcceptorExecutor.class);

    private final String id;
    private final int maxBufferSize;
    private final int maxBatchingSize;
    private final long maxBatchingDelay;

    private final AtomicBoolean isShutdown = new AtomicBoolean(false);

    private final BlockingQueue<TaskHolder<ID, T>> acceptorQueue = new LinkedBlockingQueue<>();
    private final BlockingDeque<TaskHolder<ID, T>> reprocessQueue = new LinkedBlockingDeque<>();
    private final Thread acceptorThread;

    private final Map<ID, TaskHolder<ID, T>> pendingTasks = new HashMap<>();
    private final Deque<ID> processingOrder = new LinkedList<>();

    private final Semaphore singleItemWorkRequests = new Semaphore(0);
    private final BlockingQueue<TaskHolder<ID, T>> singleItemWorkQueue = new LinkedBlockingQueue<>();

    private final Semaphore batchWorkRequests = new Semaphore(0);
    private final BlockingQueue<List<TaskHolder<ID, T>>> batchWorkQueue = new LinkedBlockingQueue<>();

    private final TrafficShaper trafficShaper;

    /*
     * Metrics
     */
    AtomicLong acceptedTasks = SpectatorUtil.monitoredLong(METRIC_REPLICATION_PREFIX + "acceptedTasks", AcceptorExecutor.class);

    AtomicLong replayedTasks = SpectatorUtil.monitoredLong(METRIC_REPLICATION_PREFIX + "replayedTasks", AcceptorExecutor.class);

    AtomicLong expiredTasks = SpectatorUtil.monitoredLong(METRIC_REPLICATION_PREFIX + "expiredTasks", AcceptorExecutor.class);

    AtomicLong overriddenTasks = SpectatorUtil.monitoredLong(METRIC_REPLICATION_PREFIX + "overriddenTasks", AcceptorExecutor.class);

    AtomicLong queueOverflows = SpectatorUtil.monitoredLong(METRIC_REPLICATION_PREFIX + "queueOverflows", AcceptorExecutor.class);

    private final PercentileDistributionSummary batchSizeMetric;

    AcceptorExecutor(String id,
                     int maxBufferSize,
                     int maxBatchingSize,
                     long maxBatchingDelay,
                     long congestionRetryDelayMs,
                     long networkFailureRetryMs) {
        this.id = id;
        this.maxBufferSize = maxBufferSize;
        this.maxBatchingSize = maxBatchingSize;
        this.maxBatchingDelay = maxBatchingDelay;
        this.trafficShaper = new TrafficShaper(congestionRetryDelayMs, networkFailureRetryMs);

        ThreadGroup threadGroup = new ThreadGroup("eurekaTaskExecutors");
        this.acceptorThread = new Thread(threadGroup, new AcceptorRunner(), "TaskAcceptor-" + id);
        this.acceptorThread.setDaemon(true);
        this.acceptorThread.start();
        
        this.batchSizeMetric = PercentileDistributionSummary.builder(Spectator.globalRegistry())
            .withName(METRIC_REPLICATION_PREFIX + "batchSize")
            .withTags(SpectatorUtil.tags(getClass()))
            .build();
        SpectatorUtil.monitoredValue(METRIC_REPLICATION_PREFIX + "reprocessQueueSize",
            this, AcceptorExecutor::getReprocessQueueSize);
        SpectatorUtil.monitoredValue(METRIC_REPLICATION_PREFIX + "acceptorQueueSize",
            this, AcceptorExecutor::getAcceptorQueueSize);
        SpectatorUtil.monitoredValue(METRIC_REPLICATION_PREFIX + "reprocessQueueSize",
            this, AcceptorExecutor::getReprocessQueueSize);
        SpectatorUtil.monitoredValue(METRIC_REPLICATION_PREFIX + "queueSize",
            this, AcceptorExecutor::getQueueSize);
        SpectatorUtil.monitoredValue(METRIC_REPLICATION_PREFIX + "pendingJobRequests",
            this, AcceptorExecutor::getPendingJobRequests);
        SpectatorUtil.monitoredValue(METRIC_REPLICATION_PREFIX + "availableJobs",
            this, AcceptorExecutor::workerTaskQueueSize);
    }

    void process(ID id, T task, long expiryTime) {
        acceptorQueue.add(new TaskHolder<ID, T>(id, task, expiryTime));
        acceptedTasks.incrementAndGet();
    }

    void reprocess(List<TaskHolder<ID, T>> holders, ProcessingResult processingResult) {
        reprocessQueue.addAll(holders);
        replayedTasks.addAndGet(holders.size());
        trafficShaper.registerFailure(processingResult);
    }

    void reprocess(TaskHolder<ID, T> taskHolder, ProcessingResult processingResult) {
        reprocessQueue.add(taskHolder);
        replayedTasks.incrementAndGet();
        trafficShaper.registerFailure(processingResult);
    }

    BlockingQueue<TaskHolder<ID, T>> requestWorkItem() {
        singleItemWorkRequests.release();
        return singleItemWorkQueue;
    }

    BlockingQueue<List<TaskHolder<ID, T>>> requestWorkItems() {
        batchWorkRequests.release();
        return batchWorkQueue;
    }

    void shutdown() {
        if (isShutdown.compareAndSet(false, true)) {
            acceptorThread.interrupt();
        }
    }

    public long getAcceptorQueueSize() {
        return acceptorQueue.size();
    }

    public long getReprocessQueueSize() {
        return reprocessQueue.size();
    }

    public long getQueueSize() {
        return pendingTasks.size();
    }

    public long getPendingJobRequests() {
        return singleItemWorkRequests.availablePermits() + batchWorkRequests.availablePermits();
    }

    public long workerTaskQueueSize() {
        return singleItemWorkQueue.size() + batchWorkQueue.size();
    }

    class AcceptorRunner implements Runnable {
        @Override
        public void run() {
            long scheduleTime = 0;
            while (!isShutdown.get()) {
                try {
                    drainInputQueues();

                    int totalItems = processingOrder.size();

                    long now = System.currentTimeMillis();
                    if (scheduleTime < now) {
                        scheduleTime = now + trafficShaper.transmissionDelay();
                    }
                    if (scheduleTime <= now) {
                        assignBatchWork();
                        assignSingleItemWork();
                    }

                    // If no worker is requesting data or there is a delay injected by the traffic shaper,
                    // sleep for some time to avoid tight loop.
                    if (totalItems == processingOrder.size()) {
                        Thread.sleep(10);
                    }
                } catch (InterruptedException ex) {
                    // Ignore
                } catch (Throwable e) {
                    // Safe-guard, so we never exit this loop in an uncontrolled way.
                    logger.warn("Discovery AcceptorThread error", e);
                }
            }
        }

        private boolean isFull() {
            return pendingTasks.size() >= maxBufferSize;
        }

        private void drainInputQueues() throws InterruptedException {
            do {
                drainReprocessQueue();
                drainAcceptorQueue();

                if (isShutdown.get()) {
                    break;
                }
                // If all queues are empty, block for a while on the acceptor queue
                if (reprocessQueue.isEmpty() && acceptorQueue.isEmpty() && pendingTasks.isEmpty()) {
                    TaskHolder<ID, T> taskHolder = acceptorQueue.poll(10, TimeUnit.MILLISECONDS);
                    if (taskHolder != null) {
                        appendTaskHolder(taskHolder);
                    }
                }
            } while (!reprocessQueue.isEmpty() || !acceptorQueue.isEmpty() || pendingTasks.isEmpty());
        }

        private void drainAcceptorQueue() {
            while (!acceptorQueue.isEmpty()) {
                appendTaskHolder(acceptorQueue.poll());
            }
        }

        private void drainReprocessQueue() {
            long now = System.currentTimeMillis();
            while (!reprocessQueue.isEmpty() && !isFull()) {
                TaskHolder<ID, T> taskHolder = reprocessQueue.pollLast();
                ID id = taskHolder.getId();
                if (taskHolder.getExpiryTime() <= now) {
                    expiredTasks.incrementAndGet();
                } else if (pendingTasks.containsKey(id)) {
                    overriddenTasks.incrementAndGet();
                } else {
                    pendingTasks.put(id, taskHolder);
                    processingOrder.addFirst(id);
                }
            }
            if (isFull()) {
                queueOverflows.addAndGet(reprocessQueue.size());
                reprocessQueue.clear();
            }
        }

        private void appendTaskHolder(TaskHolder<ID, T> taskHolder) {
            if (isFull()) {
                pendingTasks.remove(processingOrder.poll());
                queueOverflows.incrementAndGet();
            }
            TaskHolder<ID, T> previousTask = pendingTasks.put(taskHolder.getId(), taskHolder);
            if (previousTask == null) {
                processingOrder.add(taskHolder.getId());
            } else {
                overriddenTasks.incrementAndGet();
            }
        }

        void assignSingleItemWork() {
            if (!processingOrder.isEmpty()) {
                if (singleItemWorkRequests.tryAcquire(1)) {
                    long now = System.currentTimeMillis();
                    while (!processingOrder.isEmpty()) {
                        ID id = processingOrder.poll();
                        TaskHolder<ID, T> holder = pendingTasks.remove(id);
                        if (holder.getExpiryTime() > now) {
                            singleItemWorkQueue.add(holder);
                            return;
                        }
                        expiredTasks.incrementAndGet();
                    }
                    singleItemWorkRequests.release();
                }
            }
        }

        void assignBatchWork() {
            if (hasEnoughTasksForNextBatch()) {
                if (batchWorkRequests.tryAcquire(1)) {
                    long now = System.currentTimeMillis();
                    int len = Math.min(maxBatchingSize, processingOrder.size());
                    List<TaskHolder<ID, T>> holders = new ArrayList<>(len);
                    while (holders.size() < len && !processingOrder.isEmpty()) {
                        ID id = processingOrder.poll();
                        TaskHolder<ID, T> holder = pendingTasks.remove(id);
                        if (holder.getExpiryTime() > now) {
                            holders.add(holder);
                        } else {
                            expiredTasks.incrementAndGet();
                        }
                    }
                    if (holders.isEmpty()) {
                        batchWorkRequests.release();
                    } else {
                        batchSizeMetric.record(holders.size());
                        batchWorkQueue.add(holders);
                    }
                }
            }
        }

        private boolean hasEnoughTasksForNextBatch() {
            if (processingOrder.isEmpty()) {
                return false;
            }
            if (pendingTasks.size() >= maxBufferSize) {
                return true;
            }

            TaskHolder<ID, T> nextHolder = pendingTasks.get(processingOrder.peek());
            long delay = System.currentTimeMillis() - nextHolder.getSubmitTimestamp();
            return delay >= maxBatchingDelay;
        }
    }
}

package com.netflix.eureka2.eureka1.rest.registry;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscription;
import rx.functions.Action0;

/**
 * @author Tomasz Bak
 */
public class LeaseExpiryQueue {

    private final Eureka1RegistryProxyImpl registryProxy;

    private final PriorityBlockingQueue<CleanupTask> taskQueue = new PriorityBlockingQueue<>(1024, new CleanupTaskTimeComparator());
    private final Worker worker;
    private final AtomicReference<CleanupSchedulerAction> scheduledActionRef = new AtomicReference<>();

    public LeaseExpiryQueue(final Eureka1RegistryProxyImpl registryProxy, Scheduler scheduler) {
        this.registryProxy = registryProxy;
        this.worker = scheduler.createWorker();
    }

    public void enqueue(RegistrationHandler handler) {
        taskQueue.add(new CleanupTask(handler, handler.getExpiryTime()));
        scheduleCleanupTask();
    }

    public void shutdown() {
        worker.unsubscribe();
    }

    /**
     * This method may be called concurrently by a thread enqueuing new handler, and
     * from the previously scheduled action.
     */
    private void scheduleCleanupTask() {
        CleanupTask nextTask = taskQueue.peek();
        if (nextTask == null) {
            return;
        }
        CleanupSchedulerAction scheduledAction = scheduledActionRef.get();
        if (scheduledAction != null && scheduledAction.getScheduleTime() <= nextTask.getCleanupTime()) {
            return;
        }
        if (scheduledAction != null) {
            scheduledAction.cancel();
        }
        CleanupSchedulerAction nextSchedule = new CleanupSchedulerAction(nextTask.getCleanupTime());
        if (!scheduledActionRef.compareAndSet(null, nextSchedule)) {
            nextSchedule.cancel();
        }
    }

    static class CleanupTask {
        private final RegistrationHandler handler;
        private final long cleanupTime;

        CleanupTask(RegistrationHandler handler, long cleanupTime) {
            this.handler = handler;
            this.cleanupTime = cleanupTime;
        }

        public RegistrationHandler getHandler() {
            return handler;
        }

        public long getCleanupTime() {
            return cleanupTime;
        }
    }

    @SuppressWarnings("ComparatorNotSerializable")
    static class CleanupTaskTimeComparator implements Comparator<CleanupTask> {

        @Override
        public int compare(CleanupTask o1, CleanupTask o2) {
            return Long.compare(o1.getCleanupTime(), o2.getCleanupTime());
        }
    }

    class CleanupSchedulerAction implements Action0 {

        private final long scheduleTime;
        private final Subscription subscription;

        CleanupSchedulerAction(long scheduleTime) {
            this.scheduleTime = scheduleTime;
            this.subscription = worker.schedule(this, scheduleTime - worker.now(), TimeUnit.MILLISECONDS);
        }

        public long getScheduleTime() {
            return scheduleTime;
        }

        @Override
        public void call() {
            scheduledActionRef.set(null);

            long now = worker.now();
            while (!taskQueue.isEmpty() && taskQueue.peek().getCleanupTime() <= now) {
                CleanupTask task = taskQueue.remove();
                registryProxy.unregister(task.getHandler(), task.getCleanupTime());
            }

            scheduleCleanupTask();
        }

        public void cancel() {
            subscription.unsubscribe();
        }
    }
}

package com.netflix.discovery;

import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimerTask;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A supervisor task that schedules subtasks while enforce a timeout.
 * Wrapped subtasks must be thread safe.
 *
 * @author David Qiang Liu
 *
 */
public class TimedSupervisorTask extends TimerTask {
    private static final Logger logger = LoggerFactory.getLogger(TimedSupervisorTask.class);

    private static final String PREFIX = "TimedSupervisorTask_";
    private final Counter timeoutCounter;
    private final Counter rejectedCounter;
    private final Counter throwableCounter;

    private final ThreadPoolExecutor executor;
    private final int timeoutSecs;
    private final Runnable task;

    public TimedSupervisorTask(String name, ThreadPoolExecutor executor, int timeoutSecs, Runnable task) {
        this.executor = executor;
        this.timeoutSecs = timeoutSecs;
        this.task = task;

        // Initialize the counters and register.
        timeoutCounter = Monitors.newCounter(PREFIX + '_' + name + "_timeouts");
        rejectedCounter = Monitors.newCounter(PREFIX + '_' + name + "_rejectedExecutions");
        throwableCounter = Monitors.newCounter(PREFIX + '_' + name + "_throwables");
        Monitors.registerObject(this);
    }

    public void run() {
        Future future = null;
        try {
            future = executor.submit(task);
            future.get(timeoutSecs, TimeUnit.SECONDS);  // block until done or timeout
        } catch (TimeoutException e) {
            logger.error("task supervisor timed out", e);
            timeoutCounter.increment();
        } catch (RejectedExecutionException e) {
            logger.error("task supervisor rejected the task", e);
            rejectedCounter.increment();
        } catch (Throwable e) {
            logger.error("task supervisor threw an exception", e);
            throwableCounter.increment();
        } finally {
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
package com.netflix.eureka.util.batcher;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is taken from blitz4j, and slightly modified so it does not depend on bliz4j singletons.
 * <p>
 * A general purpose batcher that combines messages into batches. Callers of
 * process don't block. Configurable parameters control the number of messages
 * that may be queued awaiting processing, the maximum size of a batch, the
 * maximum time a message waits to be combined with others in a batch and the
 * size of the pool of threads that process batches.
 *
 * <p>
 * The implementation aims to avoid congestion, by working more efficiently as
 * load increases. As messages arrive faster, the collector executes less code
 * and batch sizes increase (up to the configured maximum). It should be more
 * efficient to process a batch than to process the messages individually.
 * </p>
 *
 * </p>
 * The implementation works by adding the arriving messages to a queue. The collector
 * thread takes messages from the queue and collects them into batches. When a
 * batch is big enough or old enough, the collector passes it to the processor,
 * which passes the batch to the target stream.
 * </p>
 *
 * <p>
 * The processor maintains a thread pool. If there's more work than threads, the
 * collector participates in processing by default, and consequently stops
 * collecting more batches.
 * </p>
 *
 * @author Karthik Ranganathan
 */
public class MessageBatcher<T> {

    private static final Logger logger = LoggerFactory.getLogger(MessageBatcher.class);

    private static final String BATCHER_PREFIX = "batcher.";
    private static final String COLLECTOR_SUFFIX = ".collector";

    private static final int MAX_BATCH_SIZE = 250;
    private static final int BEFORE_SHUTDOWN_WAIT_TIME_MS = 10000;
    private final boolean shouldRejectWhenFull;

    private boolean shouldCollectorShutdown;
    protected long maxDelayNano; // in nsec

    private List<T> batch;

    protected String name;

    protected BlockingQueue<T> queue;

    protected int maxMessages;

    private Collector collector;

    protected ThreadPoolExecutor processor;

    protected MessageProcessor target;

    /**
     * The number of batches that are currently being processed by the target
     * stream.
     */
    protected final AtomicInteger concurrentBatches = new AtomicInteger(0);

    protected Timer queueSizeTracer;

    protected Timer batchSyncPutTracer;

    protected Timer threadSubmitTracer;

    protected Timer processTimeTracer;

    protected Timer avgBatchSizeTracer;

    protected Counter queueOverflowCounter;

    private volatile boolean isShutDown;

    private final AtomicLong numberAdded = new AtomicLong();

    private final AtomicLong numberDropped = new AtomicLong();

    private final boolean blockingProperty;

    private boolean isCollectorPaused;

    private final Counter processCount;

    public MessageBatcher(String name,
                          MessageProcessor<T> target,
                          int maxQueueSize,
                          long maxDelayMs,
                          int minThreads,
                          int maxThreads,
                          long keepAliveTimeMs,
                          boolean shouldRejectWhenFull) {
        this.name = BATCHER_PREFIX + name;
        this.target = target;
        this.maxMessages = MAX_BATCH_SIZE;
        this.maxDelayNano = maxDelayMs * 1000000;
        this.shouldRejectWhenFull = shouldRejectWhenFull;

        queue = new ArrayBlockingQueue<T>(maxQueueSize);
        batch = new ArrayList<T>(maxMessages);
        collector = new Collector(this, this.name + COLLECTOR_SUFFIX);

        // Immediate Executor creates a factory that uses daemon threads
        createProcessor(minThreads, maxThreads, keepAliveTimeMs);
        queueSizeTracer = Monitors.newTimer("queue_size");
        batchSyncPutTracer = Monitors.newTimer("waitTimeforBuffer");
        avgBatchSizeTracer = Monitors.newTimer("batch_size");
        processCount = Monitors.newCounter("messages_processed");

        threadSubmitTracer = Monitors.newTimer("thread_invocation_time");
        processTimeTracer = Monitors.newTimer("message_processTime");
        queueOverflowCounter = Monitors.newCounter("queue_overflow");
        blockingProperty = false; // Never block
        collector.setDaemon(true);
        collector.start();
        try {
            Monitors.registerObject(this.name, this);
        } catch (Throwable e) {
            logger.warn("Metrics initialization error", e);
        }
    }

    /**
     * Checks to see if there is space available in the queue
     *
     * @return - true, if available false otherwise
     */
    public boolean isSpaceAvailable() {
        return queue.remainingCapacity() > 0;
    }

    /**
     * Processes the message sent to the batcher. This method just writes the
     * message to the queue and returns immediately. If the queue is full, the
     * messages are dropped immediately and corresponding counter is
     * incremented.
     *
     * @param message
     *            - The message to be processed
     * @return boolean - true if the message is queued for processing,false(this
     *         could happen if the queue is full) otherwise
     */
    public boolean process(T message) {
        // If this batcher has been shutdown, do not accept any more messages
        if (isShutDown) {
            return false;
        }
        try {
            queueSizeTracer.record(queue.size());
        } catch (Throwable ignored) {

        }
        if (!queue.offer(message)) {
            numberDropped.incrementAndGet();
            queueOverflowCounter.increment();
            return false;
        }
        numberAdded.incrementAndGet();
        return true;
    }

    /**
     * Processes the message sent to the batcher. This method tries to write to
     * the queue. If the queue is full, the send blocks and waits for the
     * available space.
     *
     * @param message
     *            - The message to be processed
     */
    public void processSync(T message) {
        // If this batcher has been shutdown, do not accept any more messages
        if (isShutDown) {
            return;
        }

        try {
            queueSizeTracer.record(queue.size());
        } catch (Throwable ignored) {
        }

        try {
            Stopwatch s = batchSyncPutTracer.start();
            queue.put(message);
            s.stop();
        } catch (InterruptedException e) {
            return;
        }
        numberAdded.incrementAndGet();
    }

    /**
     * Processes the messages sent to the batcher. This method just writes the
     * message to the queue and returns immediately. If the queue is full, the
     * messages are dropped immediately and corresponding counter is
     * incremented.
     *
     * @param objects
     *            - The messages to be processed
     */
    public void process(List<T> objects) {
        for (T message : objects) {
            // If this batcher has been shutdown, do not accept any more
            // messages
            if (isShutDown) {
                return;
            }
            process(message);
        }
    }

    /**
     * Processes the messages sent to the batcher. The messages are first queued
     * and then will be processed by the
     * {@link com.netflix.logging.messaging.MessageProcessor}
     *
     * @param objects
     *            - The messages to be processed
     * @param sync
     *            - if true, waits for the queue to make space, if false returns
     *            immediately after dropping the message
     */
    public void process(List<T> objects, boolean sync) {
        for (T message : objects) {
            // If this batcher has been shutdown, do not accept any more
            // messages
            if (isShutDown) {
                return;
            }
            if (sync) {
                processSync(message);
            } else {
                process(message);
            }
        }
    }

    /**
     * Pause the collector. The collector stops picking up messages from the
     * queue.
     */
    public void pause() {
        if (!isShutDown) {
            this.isCollectorPaused = true;
        }
    }

    public boolean isPaused() {
        return this.isCollectorPaused;
    }

    /**
     * Resume the collector. The collector resumes picking up messages from the
     * queue and calling the processors.
     */
    public void resume() {
        if (!isShutDown) {
            this.isCollectorPaused = false;
        }
    }

    /**
     * Stops the batcher. The Batcher has to wait for the other processes like
     * the Collector and the Executor to complete. It waits until it is notified
     * that the other processes have completed gracefully. The collector waits
     * until there are no more messages in the queue(tries 3 times waiting for
     * 0.5 seconds each) and then shuts down gracefully.
     *
     *
     */
    public void stop() {
        /*
         * Sets the shutdown flag. Future sends to the batcher are not accepted.
         * The processors wait for the current messages in the queue and with
         * the processor or collector to complete
         */
        isShutDown = true;

        int waitTimeinMillis = BEFORE_SHUTDOWN_WAIT_TIME_MS;
        long timeToWait = waitTimeinMillis + System.currentTimeMillis();
        while ((!queue.isEmpty() || !batch.isEmpty()) && System.currentTimeMillis() < timeToWait) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        try {
            shouldCollectorShutdown = true;
            processor.shutdownNow();
        } catch (Throwable e) {
            logger.warn("Message batcher shutdown completed with an error", e);
        }
    }

    /**
     * The class that processes the messages in a batch by calling the
     * implementor of the MessageProcessor interface.
     *
     *
     */
    private static class ProcessMessages<T> implements Runnable {

        private final MessageBatcher<T> stream;
        private final List<T> batch;

        private final Timer processMessagesTracer;
        private final Timer avgConcurrentBatches;

        private ProcessMessages(MessageBatcher<T> stream, List<T> batch) {
            this.stream = stream;
            this.batch = batch;
            this.processMessagesTracer = stream.processTimeTracer;
            this.avgConcurrentBatches = Monitors.newTimer(stream.name + ".concurrentBatches");
        }

        /** Process the batch. */
        @Override
        public void run() {
            try {
                if (batch == null) {
                    return;
                }
                int inProcess = stream.concurrentBatches.incrementAndGet();
                try {
                    avgConcurrentBatches.record(inProcess);

                    Stopwatch s = processMessagesTracer.start();
                    stream.target.process(batch);
                    s.stop();
                } finally {
                    stream.concurrentBatches.decrementAndGet();
                }
            } catch (Throwable e) {
                logger.error("Batch processing failure", e);
            }
        }
    }

    private class Collector extends Thread {
        private static final int SLEEP_TIME_MS = 1;
        private static final int RETRY_EXECUTION_TIMEOUT_MS = 1;

        private final Timer processTimeTracer;
        private final Counter rejectedCounter = Monitors.newCounter(processCount + ".rejected");

        private final MessageBatcher<T> stream;

        private final Timer queueSizeTracer;

        private Collector(MessageBatcher<T> stream, String name) {
            super(name);
            processTimeTracer = Monitors.newTimer(name + ".processTime");
            this.stream = stream;
            queueSizeTracer = Monitors.newTimer(name + ".queue_size_at_drain");
        }

        /** Process messages from the queue, after grouping them into batches. */
        @Override
        public void run() {
            int batchSize;
            while (!shouldCollectorShutdown) {
                if (isCollectorPaused) {
                    try {
                        Thread.sleep(SLEEP_TIME_MS);
                    } catch (InterruptedException ignore) {
                    }
                    continue;
                }
                try {
                    if (batch.size() < stream.maxMessages) {
                        long now = System.nanoTime();
                        final long firstTime = now;
                        do {
                            if (stream.queue.drainTo(batch, stream.maxMessages
                                    - batch.size()) <= 0) {
                                long maxWait = firstTime + stream.maxDelayNano
                                        - now;
                                if (maxWait <= 0) { // timed out
                                    break;
                                }
                                // Wait for a message to arrive:
                                T nextMessage = null;
                                try {
                                    nextMessage = stream.queue.poll(maxWait, TimeUnit.NANOSECONDS);
                                } catch (InterruptedException ignore) {
                                }
                                if (nextMessage == null) { // timed out
                                    break;
                                }
                                batch.add(nextMessage);
                                now = System.nanoTime();
                            }
                        } while (batch.size() < stream.maxMessages);
                    }
                    batchSize = batch.size();
                    if (batchSize > 0) {
                        try {
                            queueSizeTracer.record(stream.queue.size());
                        } catch (Exception ignored) {
                        }
                        avgBatchSizeTracer.record(batchSize);
                        Stopwatch s = processTimeTracer.start();
                        boolean retryExecution;
                        do {
                            try {
                                stream.processor.execute(new ProcessMessages<>(stream, batch));
                                retryExecution = false;
                            } catch (RejectedExecutionException re) {
                                rejectedCounter.increment();
                                retryExecution = true;
                                Thread.sleep(RETRY_EXECUTION_TIMEOUT_MS);
                            }
                        } while (retryExecution);
                        processCount.increment(batchSize);
                        s.stop();
                        batch = new ArrayList(stream.maxMessages);
                    }
                } catch (Throwable e) {
                    logger.error("Collector task error", e);
                }
            } // - while (!shutdownCollector)
        } // - run()

    }

    /**
     * The size of the the queue in which the messages are batches
     *
     * @return- size of the queue
     */
    @Monitor(name = "batcherQueueSize", type = DataSourceType.GAUGE)
    public int getSize() {
        if (queue != null) {
            return queue.size();
        } else {
            return 0;
        }
    }

    /**
     * Gets the statistics count of number of messages added to this batcher.
     */
    @Monitor(name = "numberAdded", type = DataSourceType.GAUGE)
    public long getNumberAdded() {
        return numberAdded.get();
    }

    /**
     * Gets the statistics count of number of messages dropped by this batcher.
     */
    @Monitor(name = "numberDropped", type = DataSourceType.GAUGE)
    public long getNumberDropped() {
        return numberDropped.get();
    }

    /**
     * Gets the information whether the batcher is blocking or not blocking. By
     * default, the batcher is non-blocking and the messages are just dropped if
     * the queue is full.
     *
     * If the batcher is made blocking, the sends block and wait indefinitely
     * until space is made in the batcher.
     *
     * @return - true if blocking, false otherwise
     */
    @Monitor(name = "blocking", type = DataSourceType.INFORMATIONAL)
    public boolean isBlocking() {
        return blockingProperty;
    }

    private void createProcessor(int minThreads, int maxThreads, long keepAliveTimeMs) {

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setDaemon(false).setNameFormat(this.name + "-process").build();

        this.processor = new ThreadPoolExecutor(
                minThreads, maxThreads,
                keepAliveTimeMs, TimeUnit.MILLISECONDS,
                new SynchronousQueue(),
                threadFactory
        );
        if (!shouldRejectWhenFull) {
            this.processor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                    super.rejectedExecution(r, e);
                }
            });
        }
    }
}

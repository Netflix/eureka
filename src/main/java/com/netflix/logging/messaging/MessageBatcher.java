package com.netflix.logging.messaging;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicIntProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * A general purpose batcher that combines messages into batches. Callers of
 * process don't block. Configurable parameters control the number of messages
 * that may be queued awaiting processing, the maximum size of a batch, the
 * maximum time a message waits to be combined with others in a batch and the
 * size of the pool of threads that process batches.
 * 
 * The implementation aims to avoid congestion, by working more efficiently as
 * load increases. As messages arrive faster, the collector executes less code
 * and batch sizes increase (up to the configured maximum). It should be more
 * efficient to process a batch than to process the messages individually.
 * 
 * Here's how it works. Arriving messages are added to the queue. The collector
 * thread takes messages from the queue and collects them into batches. When a
 * batch is big enough or old enough, the collector passes it to the processor,
 * which passes the batch to the target stream.
 * 
 * The processor maintains a thread pool. If there's more work than threads, the
 * collector participates in processing, and consequently stops collecting more
 * batches.
 * 
 * 
 */
public class MessageBatcher<T> {

    private static final String DOT = ".";

    private static final String BATCHER_PREFIX = "batcher.";

    private static final String COLLECTOR_SUFFIX = ".collector";

    private static final double BATCH_MAX_DELAY_SECONDS = 0.5;

    private static final String BATCH_WAIT_TIME_MS = "waitTimeinMillis";

    private static final String BATCH_MAX_DELAY = "batch.maxDelay";

    private static final String BATCH_MAX_MESSAGES = "batch.maxMessages";

    private static final String QUEUE_MAX_MESSAGES = "queue.maxMessages";

    private boolean shouldCollectorShutdown;

    List<Object> batch;

    protected String name;

    protected BlockingQueue queue;

    protected int maxMessages;

    protected static long maxDelay; // in nsec

    protected Collector collector;

    protected ThreadPoolExecutor processor;

    protected MessageProcessor target = null;

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

    private boolean isShutDown;

    private AtomicLong numberAdded = new AtomicLong();

    private AtomicLong numberDropped = new AtomicLong();

    private DynamicBooleanProperty blockingProperty;

    private boolean isCollectorPaused;

    private Counter processCount;

    private static final Logger logger = LoggerFactory
    .getLogger(MessageBatcher.class);
    public static final String POOL_MAX_THREADS = "maxThreads";
    private static final int DEFAULT_POOL_MAX_THREADS = 20;

    public static final String POOL_MIN_THREADS = "minThreads";
    private static final int DEFAULT_POOL_MIN_THREADS = 10;
    public static final String POOL_KEEP_ALIVE_TIME = "keepAliveTime";
    private static final long DEFAULT_POOL_KEEP_ALIVE_TIME = 15 * 60L;

    /** Get initial parameters from Netflix Configuration */
    public MessageBatcher(String name, MessageProcessor target) {
        this.name = BATCHER_PREFIX + name;
        this.target = target;
        queue = new ArrayBlockingQueue(DynamicPropertyFactory.getInstance()
                .getIntProperty(this.name + DOT + QUEUE_MAX_MESSAGES, 10000)
                .get());
        logger.info("Array size for batcher :" + name + ":"
                + queue.remainingCapacity());
        setBatchMaxMessages(DynamicPropertyFactory.getInstance()
                .getIntProperty(this.name + DOT + BATCH_MAX_MESSAGES, 30).get());
        batch = new ArrayList(maxMessages);
        setBatchMaxDelay(DynamicPropertyFactory
                .getInstance()
                .getDoubleProperty(this.name + DOT + BATCH_MAX_DELAY,
                        BATCH_MAX_DELAY_SECONDS).get());
        collector = new Collector(this, this.name + COLLECTOR_SUFFIX);
        // Immediate Executor creates a factory that uses daemon threads
        createProcessor(this.name);
        queueSizeTracer = Monitors.newTimer(this.name
                + ".queue.size at offer [messages, not msec]");
        batchSyncPutTracer = Monitors.newTimer(this.name + ".syncput");
        avgBatchSizeTracer = Monitors.newTimer(this.name
                + ".batch.size [messages, not msec]");
        processCount = Monitors.newCounter(this.name + ".processCount");

        threadSubmitTracer = Monitors.newTimer(this.name + ".threadSubmitTime");
        processTimeTracer = Monitors.newTimer(this.name + ".processTime");
        queueOverflowCounter = Monitors.newCounter(this.name
                + ".queue.overflow");
        blockingProperty = DynamicPropertyFactory.getInstance()
        .getBooleanProperty(this.name + DOT + "blocking", false);
        collector.setDaemon(true);
        collector.start();
        try {
            Monitors.registerObject(this.name, this);
        } catch (Throwable e) {
            logger.warn("Cannot register the JMX monitor for the batcher :"
                    + this.name, e);
        }
    }

    private void createProcessor(String name) {
        DynamicIntProperty minThreads = DynamicPropertyFactory.getInstance()
        .getIntProperty(name + DOT + POOL_MIN_THREADS, 1);
        DynamicIntProperty maxThreads = DynamicPropertyFactory.getInstance()
        .getIntProperty(name + DOT + POOL_MAX_THREADS, 3);
        DynamicIntProperty keepAliveTime = DynamicPropertyFactory.getInstance()
        .getIntProperty(name + DOT + POOL_KEEP_ALIVE_TIME, 15 * 60);
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(false).setNameFormat(this.name + "-process").build();

        this.processor = new ThreadPoolExecutor(minThreads.get(),
                maxThreads.get(), keepAliveTime.get(), TimeUnit.SECONDS,
                new SynchronousQueue(), threadFactory);
        DynamicBooleanProperty shouldRejectWhenFull = DynamicPropertyFactory
        .getInstance().getBooleanProperty(
                name + DOT + "rejectWhenFull", false);
        if (!shouldRejectWhenFull.get()) {
            this.processor
            .setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy() {
                @Override
                public void rejectedExecution(Runnable r,
                        ThreadPoolExecutor e) {
                    super.rejectedExecution(r, e);
                }
            });
        }

    }

    /** Set the stream that will process each batch of messages. */
    public synchronized void setTarget(MessageProcessor target) {
        this.target = target;
    }

    /**
     * Set the maximum number of messages in a batch. Setting this to 1 will
     * prevent batching; that is, messages will be passed to
     * target.processMessage one at a time.
     */
    public synchronized void setBatchMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    /**
     * Set the maximum time a message spends waiting to complete a full batch,
     * in seconds. This doesn't limit the time spent in the queue.
     */
    public synchronized void setBatchMaxDelay(double maxDelaySec) {
        maxDelay = (long) (maxDelaySec * 1000000000);
    }

    /** Set the number of threads that process batches. */
    void setProcessorMaxThreads(int threads) {
        if (processor.getCorePoolSize() > threads) {
            processor.setCorePoolSize(threads);
        }
        processor.setMaximumPoolSize(threads);
    }

    /**
     * Checks to see if there is space available in the queue
     * 
     * @return - true, if available false otherwise
     */
    public boolean isSpaceAvailable() {
        return (queue.remainingCapacity() > 0);
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
     * @param message
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
     * @param message
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

        DynamicIntProperty waitTimeinMillis = DynamicPropertyFactory
        .getInstance().getIntProperty(name + "." + BATCH_WAIT_TIME_MS,
                10000);
        long timeToWait = waitTimeinMillis.get() + System.currentTimeMillis();
        logger.info("Batcher size for : " + name + " is :" + queue.size());

        while ((queue.size() > 0 || batch.size() > 0)
                && (System.currentTimeMillis() < timeToWait)) {
            logger.info("Waiting for batched messages to be sent...");
            logger.info("Batch size :" + queue.size());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        try {
            logger.info("Shutting down collector");
            shouldCollectorShutdown = true;
            logger.info("Shutting down processor");
            processor.shutdownNow();
            /*
             * processor.awaitTermination(10000, TimeUnit.SECONDS); if
             * (!processor.isShutdown()) { processor.shutdownNow(); }
             */
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        /*
         * Sets the shutdown flag. Future sends to the batcher are not accepted.
         * The processors wait for the current messages in the queue and with
         * the processor or collector to complete
         */
        isShutDown = true;
        logger.info("Finished shutting down batcher " + name);

    }

    /**
     * The class that processes the messages in a batch by calling the
     * implementor of the MessageProcessor interface.
     * 
     * @author kranganathan
     * 
     */
    private static class ProcessMessages implements Runnable {
        public ProcessMessages(MessageBatcher stream, List batch) {
            this.stream = stream;
            this.batch = batch;
            this.processMessagesTracer = stream.processTimeTracer;
            this.avgConcurrentBatches = Monitors.newTimer(stream.name
                    + ".concurrentBatches");
        }

        private final MessageBatcher stream;

        private List batch;

        private Timer processMessagesTracer;
        private Timer avgConcurrentBatches;

        /** Process the batch. */
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
                e.printStackTrace();
            }
        }
    }

    private class Collector extends Thread {
        private static final int SLEEP_TIME_MS = 1;
        private Timer processTimeTracer;
        private Counter rejectedCounter = Monitors.newCounter(processCount
                + ".rejected");
        private static final int RETRY_EXECUTION_TIMEOUT_MS = 1;

        public Collector(MessageBatcher stream, String name) {
            super(name);
            processTimeTracer = Monitors.newTimer(name + ".processTime");
            this.stream = stream;
            queueSizeTracer = Monitors.newTimer(name
                    + ".queue.size at drain [messages, not msec]");
        }

        private final MessageBatcher stream;

        private final Timer queueSizeTracer;

        /** Process messages from the queue, after grouping them into batches. */
        public void run() {

            int batchSize = 0;
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
                                long maxWait = firstTime + stream.maxDelay
                                - now;
                                if (maxWait <= 0) { // timed out
                                    break;
                                }
                                // Wait for a message to arrive:
                                Object nextMessage = null;
                                try {
                                    nextMessage = stream.queue.poll(maxWait,
                                            TimeUnit.NANOSECONDS);
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
                        boolean retryExecution = false;
                        do {
                            try {
                                stream.processor.execute(new ProcessMessages(
                                        stream, batch));
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
                    e.printStackTrace();
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
     * Resets the statistics that keeps the count of number of messages added to
     * this batcher.
     */
    public void resetNumberAdded() {
        numberAdded.set(0);
    }

    /**
     * Resets the statistics that keeps the count of number of messages dropped
     * by this batcher.
     */

    public void resetNumberDropped() {
        numberDropped.set(0);
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
        return blockingProperty.get();
    }

}

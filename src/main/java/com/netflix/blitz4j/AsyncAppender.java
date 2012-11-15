package com.netflix.blitz4j;

import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.AppenderAttachableImpl;
import org.apache.log4j.spi.AppenderAttachable;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.logging.messaging.BatcherFactory;
import com.netflix.logging.messaging.MessageBatcher;
import com.netflix.logging.messaging.MessageProcessor;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.monitor.DynamicCounter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;

/**
 * A log4j appender implementation that logs the events asynchronously after
 * storing the events in a buffer. The buffer implementation uses an instance of
 * {@link com.netflix.logging.messaging.MessageBatcher}.
 * 
 * Incoming events are first stored in a queue and then a single worker thread
 * takes the messages and writes it to the underlying appenders. This makes the
 * logging of the messages efficient for the following reasons
 * 
 * 1) Logging threads do not block until the event is written to the
 * destination, but block only until the message is written to the queue which
 * should be way faster than having to wait until it is written to the
 * underlying destination
 * 
 * 2) During log storms, the in-memory buffer overflows the message to another
 * structure which logs just the summary and not each log message
 * 
 * By default the buffer holds up to 10K messages and summary up to 5K entries.
 * Depending on the memory constraints and logging frequency, both these are
 * configurable. The summary also starts dropping its entries when it stays
 * there longer than 1 min which is configurable as well.
 * 
 * @author kranganathan
 * 
 */
public class AsyncAppender extends AppenderSkeleton implements
        AppenderAttachable {

    private static final int SLEEP_TIME_MS = 1;
    private static final int DEFAULT_DISCARD_ENTRY_EXPIRE_SECONDS = 60;
    private static final int DEFAULT_DISCARD_MAP_SIZE = 10000;
    private static final String BATCHER_NAME_LIMITER = ".";
    private static final String APPENDER_NAME = "ASYNC";
    private MessageBatcher<LoggingEvent> batcher;
    private String originalAppenderName;
    private static final String LOGGER_ASYNC_APPENDER = "asyncAppenders";
    private boolean shouldSummarizeOverflow;

    private AppenderAttachableImpl appenders = new AppenderAttachableImpl();

    // The Map to the summary events
    private ConcurrentMap<String, DiscardSummary> discardMap = new ConcurrentHashMap<String, DiscardSummary>();

    private Timer putBufferTimeTracer;
    private Timer putDiscardMapTimeTracer;
    private DynamicBooleanProperty generatelocationInfo = DynamicPropertyFactory
            .getInstance().getBooleanProperty(
                    "netflix.blitz4j.generateLocationInfo", true);

    private boolean defaultLocationInfo = false;

    public boolean getLocationInfo() {
        return this.defaultLocationInfo;
    }

    public void setLocationInfo(boolean locationInfo) {
        this.defaultLocationInfo = locationInfo;
    }

    private static final Logger logger = LoggerFactory
            .getLogger(AsyncAppender.class);

    public AsyncAppender() {
        this.name = APPENDER_NAME;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime
                * result
                + ((originalAppenderName == null) ? 0 : originalAppenderName
                        .hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AsyncAppender other = (AsyncAppender) obj;
        if (originalAppenderName == null) {
            if (other.originalAppenderName != null)
                return false;
        } else if (!originalAppenderName.equals(other.originalAppenderName))
            return false;
        return true;
    }

    /**
     * Initialize the batcher that stores the messages and calls the underlying
     * appenders.
     * 
     * @param appenderName
     *            - The name of the appender for which the batcher is created
     */
    private void initBatcher(String appenderName) {
        MessageProcessor<LoggingEvent> messageProcessor = new MessageProcessor<LoggingEvent>() {
            @Override
            public void process(List<LoggingEvent> objects) {
                processLoggingEvents(objects);
            }
        };
        String batcherName = this.getClass().getName() + BATCHER_NAME_LIMITER
                + appenderName;
        batcher = BatcherFactory.createBatcher(batcherName, messageProcessor);
        batcher.setTarget(messageProcessor);

    }

    /**
     * Process the logging events. This is called by the batcher.
     * 
     * @param loggingEvents
     *            - The logging events to be written to the underlying appender
     */
    private void processLoggingEvents(List<LoggingEvent> loggingEvents) {
        // Lazy initialization of the appender. This is needed because the
        // original
        // appenders configuration may be available only after the complete
        // log4j initializaiton.
        while (appenders.getAllAppenders() == null) {
            if ((batcher == null) || (batcher.isPaused())) {
                try {
                    Thread.sleep(SLEEP_TIME_MS);
                } catch (InterruptedException ignore) {

                }
                continue;
            }

            org.apache.log4j.Logger asyncLogger = LoggerCache.getInstance()
                    .getOrCreateLogger(LOGGER_ASYNC_APPENDER);
            Appender originalAppender = asyncLogger
                    .getAppender(originalAppenderName);
            if (originalAppender == null) {
                try {
                    Thread.sleep(SLEEP_TIME_MS);
                } catch (InterruptedException ignore) {

                }
                continue;
            }
            appenders.addAppender(originalAppender);
        }
        // First take the overflown summary events and put it back in the
        // queue
        for (Iterator<Entry<String, DiscardSummary>> iter = discardMap
                .entrySet().iterator(); iter.hasNext();) {
            Entry<String, DiscardSummary> mapEntry = (Entry<String, DiscardSummary>) iter
                    .next();
            // If the space is not available, then exit immediately
            if (batcher.isSpaceAvailable()) {
                DiscardSummary discardSummary = mapEntry.getValue();
                LoggingEvent event = discardSummary.createEvent();
                // Put the event in the queue and remove the event from the
                // summary
                if (batcher.process(event)) {
                    iter.remove();
                } else {
                    break;
                }
            } else {
                break;
            }

        }
        // Process the events from the queue and call the underlying
        // appender
        for (LoggingEvent event : loggingEvents) {
            appenders.appendLoopOnAppenders(event);

        }

    }

    /**
     * {@inheritDoc}
     */
    public void append(final LoggingEvent event) {
        boolean isBufferSpaceAvailable = (batcher.isSpaceAvailable() && (discardMap
                .size() == 0));
        boolean isBufferPutSuccessful = false;
        LocationInfo locationInfo = null;
        // Reject it when we have a fast property as these can be expensive
        if (this.shouldSummarizeOverflow) {
            if (generatelocationInfo.get()) {
                locationInfo = LoggingContext.getInstance()
                        .generateLocationInfo(event);
            } else if (defaultLocationInfo) {
                locationInfo = event.getLocationInformation();
            }
        }

        if (isBufferSpaceAvailable) {
            // Save the thread local info in the event so that the
            // processing threads
            // can have access to the thread local of the arriving event
            saveThreadLocalInfo(event);
            isBufferPutSuccessful = putInBuffer(event);
        }
        // If the buffer is full, then summarize the information
        if (shouldSummarizeOverflow && (!isBufferPutSuccessful)) {
            DynamicCounter.increment(this.originalAppenderName
                    + "_summarizeEvent", null);
            Stopwatch t = putDiscardMapTimeTracer.start();
            String loggerKey = event.getLoggerName();
            if (locationInfo != null) {
                loggerKey = locationInfo.getClassName() + "_"
                        + locationInfo.getLineNumber();
            }

            DiscardSummary summary = (DiscardSummary) discardMap.get(loggerKey);
            if (summary == null) {
                // Saving the thread local info is needed only for the first
                // time
                // creation of the summary
                saveThreadLocalInfo(event);
                summary = new DiscardSummary(event);
                discardMap.put(loggerKey, summary);
            } else {
                // The event summary is already there, just increment the
                // count
                summary.add(event);
            }
            t.stop();
        } else if (!shouldSummarizeOverflow && (!isBufferPutSuccessful)) {
            // Record the event that are not summarized and which are just
            // discarded
            DynamicCounter.increment(this.originalAppenderName
                    + "_discardEvent", null);
        }

    }

    /**
     * Sets the name of the underlying appender that is wrapped by this
     * <code>AsyncAppender</code>
     * 
     * @param name
     *            - The name of the underlying appender
     */
    public void setOriginalAppenderName(String name) {
        this.originalAppenderName = name;
        this.initBatcher(this.originalAppenderName);
        this.putBufferTimeTracer = Monitors.newTimer("putBuffer",
                TimeUnit.NANOSECONDS);
        this.putDiscardMapTimeTracer = Monitors.newTimer("putDiscardMap",
                TimeUnit.NANOSECONDS);
        this.discardMap = CacheBuilder
                .newBuilder()
                .initialCapacity(5000)
                .maximumSize(
                        DynamicProperty.getInstance(
                                "netflix.blitz4j." + this.originalAppenderName
                                        + "maxDiscardMapSize").getInteger(
                                DEFAULT_DISCARD_ENTRY_EXPIRE_SECONDS))
                .expireAfterWrite(
                        DynamicProperty.getInstance(
                                "netflix.blitz4j." + this.originalAppenderName
                                        + "expireDiscardEntryInSeconds")
                                .getInteger(DEFAULT_DISCARD_MAP_SIZE),
                        TimeUnit.SECONDS).<String, DiscardSummary> build()
                .asMap();
        try {
            Monitors.registerObject(this.originalAppenderName, this);
        } catch (Throwable e) {
            e.printStackTrace();
            logger.error("Cannot register monitor for AsyncAppender", e);
        }
    }

    /**
     * Sets a flag to summarize the overflow information.
     * 
     * @param summarizeOverflow
     *            - true to summarize, false to not
     */
    public void setSummarizeOverflow(boolean summarizeOverflow) {
        this.shouldSummarizeOverflow = summarizeOverflow;
    }

    /**
     * Checks if the events overflowing the buffer should be summarized.
     * 
     * @return - true, if the overflowing events need to be summarized, false
     *         otherwise
     */
    public boolean isShouldSummarizeOverflow() {
        return shouldSummarizeOverflow;
    }

    /**
     * Save the thread local info of the event in the event itself for
     * processing later.
     * 
     * @param event
     *            - The logging event for which the information should be saved
     */
    private void saveThreadLocalInfo(final LoggingEvent event) {
        // Set the NDC and thread name for the calling thread as these
        // LoggingEvent fields were not set at event creation time.
        event.getNDC();
        event.getThreadName();
        // Get a copy of this thread's MDC.
        event.getMDCCopy();
    }

    /**
     * Puts the logging events to the in-memory buffer.
     * 
     * @param event
     *            - The event that needs to be put in the buffer.
     * @return - true, if the put was successful, false otherwise
     */
    private boolean putInBuffer(final LoggingEvent event) {
        DynamicCounter.increment(this.originalAppenderName
                + "_putInBuffer", null);
        Stopwatch t = putBufferTimeTracer.start();
        boolean hasPut = false;
        if (batcher.process(event)) {
            hasPut = true;
        } else {
            hasPut = false;
        }
        t.stop();
        return hasPut;
    }

    /**
     * Summary of discarded logging events for a logger.
     */
    private static final class DiscardSummary {
        /**
         * First event of the highest severity.
         */
        private LoggingEvent maxEvent;

        /**
         * Total count of messages discarded.
         */
        private int count;

        /**
         * Create new instance.
         * 
         * @param event
         *            event, may not be null.
         */
        public DiscardSummary(final LoggingEvent event) {
            count = 1;
            this.maxEvent = event;
        }

        /**
         * Add discarded event to summary.
         * 
         * @param event
         *            event, may not be null.
         */
        public void add(final LoggingEvent event) {
            count++;
        }

        /**
         * Create event with summary information.
         * 
         * @return new event.
         */
        public LoggingEvent createEvent() {
            String msg = MessageFormat
                    .format("{1}[Discarded {0} messages of this type due to buffer full condition]",
                            new Object[] { new Integer(count),
                                    maxEvent.getMessage() });
            LoggingEvent event = new LoggingEvent(
                    maxEvent.getFQNOfLoggerClass(), maxEvent.getLogger(),
                    maxEvent.getTimeStamp(), maxEvent.getLevel(), msg, Thread
                            .currentThread().getName(),
                    maxEvent.getThrowableInformation(), null, null,
                    maxEvent.getProperties());
            return event;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.AppenderSkeleton#close()
     */
    @Override
    public void close() {
        synchronized (appenders) {
            appenders.removeAllAppenders();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.spi.AppenderAttachable#getAllAppenders()
     */
    @Override
    public Enumeration getAllAppenders() {
        synchronized (appenders) {
            return appenders.getAllAppenders();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.spi.AppenderAttachable#getAppender(java.lang.String)
     */
    @Override
    public Appender getAppender(final String name) {
        synchronized (appenders) {
            return appenders.getAppender(name);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.spi.AppenderAttachable#isAttached(org.apache.log4j.Appender
     * )
     */
    @Override
    public boolean isAttached(final Appender appender) {
        synchronized (appenders) {
            return appenders.isAttached(appender);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.AppenderSkeleton#requiresLayout()
     */
    @Override
    public boolean requiresLayout() {
        return false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.log4j.spi.AppenderAttachable#removeAllAppenders()
     */
    @Override
    public void removeAllAppenders() {
        synchronized (appenders) {
            appenders.removeAllAppenders();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.spi.AppenderAttachable#removeAppender(org.apache.log4j
     * .Appender)
     */
    @Override
    public void removeAppender(final Appender appender) {
        synchronized (appenders) {
            appenders.removeAppender(appender);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.spi.AppenderAttachable#removeAppender(java.lang.String)
     */
    @Override
    public void removeAppender(final String name) {
        synchronized (appenders) {
            appenders.removeAppender(name);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.log4j.spi.AppenderAttachable#addAppender(org.apache.log4j.
     * Appender)
     */
    @Override
    public void addAppender(final Appender newAppender) {
        synchronized (appenders) {
            appenders.addAppender(newAppender);
        }
    }

    @com.netflix.servo.annotations.Monitor(name = "discardMapSize", type = DataSourceType.GAUGE)
    public int getDiscadMapSize() {
        return discardMap.size();
    }

}

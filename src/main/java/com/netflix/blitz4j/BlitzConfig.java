package com.netflix.blitz4j;

public interface BlitzConfig {

    /**
     * Indicates whether blitz4j should use its less contended implementation.
     * 
     * @return
     */
    public abstract boolean shouldUseLockFree();

    /**
     * Indicates whether blitz4j should print the errors during logging for
     * debugging.
     * 
     * @return
     */
    public abstract boolean shouldPrintLoggingErrors();

    /**
     * Get the list of asynchronous appenders. The configuration is specified
     * similar to any log4j loging override
     * (ie)log4j.logger.asyncAppenders=INFO,MYAPPENDER. The logging level in
     * this definitions bears no specific significance and is only for
     * completion.
     * 
     * @return
     */
    public abstract String[] getAsyncAppenders();

    /**
     * Get the time in seconds that the summary that is stored will expire.
     * 
     * @param originalAppenderName
     *            - The name of the appender for which the logging is done
     * @return
     */
    public abstract int getLogSummaryExpiry(String originalAppenderName);

    /**
     * Get the size of the log summary information.
     * 
     * @param originalAppenderName
     *            - The name of the appender for which the logging is done
     * @return
     */
    public abstract int getLogSummarySize(String originalAppenderName);

    /**
     * Checks whether the blitz4j based location information is generated or
     * not.
     * 
     * @return - true, if the location information need to be generated, false
     *         otherwise.
     */
    public abstract boolean shouldGenerateBlitz4jLocationInfo();

    /**
     * Checks whether the log4j based location information is generated or not.
     * 
     * @return - true, if the location information need to be generated, false
     *         otherwise.
     */
    public abstract boolean shouldGenerateLog4jLocationInfo();

    /**
     * Checks whether the summary information should be generated when the
     * asynchronous buffer becomes full.
     * 
     * @param originalAppenderName
     *            - The appender name for which the logging is done
     * @return - true, if the information should be summarized, false otherwise
     */
    public abstract boolean shouldSummarizeOverflow(String originalAppenderName);

    /**
     * Get the list of asynchronous appender names so that they can be treated a
     * little differently during dynamic reconfiguration.
     * 
     * @return - The list of asynchronous appender names
     */
    public abstract String[] getAsyncAppenderImplementationNames();

    /**
     * Gets the maximum number of messages allowed in the buffer.
     * 
     * @param batcherName
     *            - The name of the batcher
     * @return - an integer value denoting the size of the buffer
     */
    public abstract int getBatcherQueueMaxMessages(String batcherName);

    /**
     * Gets the batch size of each batch for which the log processing is done.
     * 
     * @param batcherName
     *            - The name of the batcher
     * @return - an integer value denoting the size of the batch
     */
    public abstract int getBatchSize(String batcherName);

    /**
     * Get the time to wait before the batcher flushes out all its messages in
     * the buffer.
     * 
     * @param batcherName
     *            - The name of the batcher
     * @return - time in seconds
     */
    public abstract int getBatcherWaitTimeBeforeShutdown(String batcherName);

    /**
     * Gets the time to wait for the messages to be batcher before it is given
     * to processor threads.
     * 
     * @param batcherName
     *            - The name of the batcher.
     * @return - double value in seconds
     */
    public abstract double getBatcherMaxDelay(String batcherName);

    /**
     * Checks to see whether the caller threads should block and wait if the
     * internal buffer is full.
     * 
     * @param batcherName
     *            - The name of the batcher.
     * @return - true, if the caller threads should block and wait, false
     *         otherwise.
     */
    public abstract boolean shouldWaitWhenBatcherQueueNotEmpty(
            String batcherName);

    /**
     * Gets the minimum number of processing threads that should be run to
     * handle the messages.
     * 
     * @param batcherName
     *            - The name of the batcher.
     * @return - an integer value indicating the minimum number of threads to be
     *         run
     */
    public abstract int getBatcherMinThreads(String batcherName);

    /**
     * Gets the maximum number of processing threads that should be run to
     * handle the messages.
     * 
     * @param batcherName
     *            - The name of the batcher
     * @return - an integer value indicating the maximum number of threads to be
     *         run
     */
    public abstract int getBatcherMaxThreads(String batcherName);

    /**
     * Gets the time to keep the processing threads alive when they are idle.
     * 
     * @param batcherName
     *            - The name of the batcher
     * @return - time in seconds
     */
    public abstract int getBatcherThreadKeepAliveTime(String batcherName);

    /**
     * Checks to see if the collector threads that hands the message to the
     * processor threads should participate in processing or not when all the
     * threads are used up.
     * 
     * @param batcherName
     *            - The name of the batcher
     * @return - true if the collector threads participates in processing, false
     *         if the processing is rejected. If the processing is rejected, it
     *         is retried indefinitely.
     */
    public abstract boolean shouldRejectWhenAllBatcherThreadsUsed(
            String batcherName);
    
    /**
     * Checks to see if the log4j.properties should be loaded from classpath during configuration.
     * @return true, if log4j.properties need to be loaded, false otherwise.
     */
    public boolean shouldLoadLog4jPropertiesFromClassPath();

}
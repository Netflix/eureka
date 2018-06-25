package com.netflix.eureka.test.async.executor;

/**
 * Backoff for pausing for a duration
 */
public class Backoff {

    /**
     * Pause time in milliseconds
     */
    private final int pauseTimeInMs;

    /**
     * Prepare to pause for one duration.
     *
     * @param pauseTimeInMs pause time in milliseconds
     */
    public Backoff(int pauseTimeInMs) {
        this.pauseTimeInMs = pauseTimeInMs;
    }

    /**
     * Backoff for one duration.
     */
    public void backoff() throws InterruptedException {
        Thread.sleep(pauseTimeInMs);
    }
}

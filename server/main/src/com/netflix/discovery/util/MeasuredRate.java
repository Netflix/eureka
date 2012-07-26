/*
 * MeasuredRate.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.util;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for getting a count in last X milliseconds
 * 
 * @author gkim
 */
public class MeasuredRate {
    private final AtomicLong _lastBucket = new AtomicLong(0);
    private final AtomicLong _currentBucket = new AtomicLong(0);
    private final long _sampleInterval;
    private Timer timer =  new Timer("MeasureRateTimer", true);
    private static final Logger logger = LoggerFactory.getLogger(MeasuredRate.class); 
    
    /** 
     * @param sampleInterval in milliseconds
     */
    public MeasuredRate(long sampleInterval){
       _sampleInterval = sampleInterval;
     timer.schedule(new TimerTask(){

        @Override
        public void run() {
            try {
            _lastBucket.set(_currentBucket.getAndSet(0));
            }
            catch (Throwable e) {
              logger.error("Cannot reset the Measured Rate", e) ;
            }
        }}, _sampleInterval, _sampleInterval);
    }
    
    /**
     * Returns the count in the last sample interval
     */
    public long getCount() {
        return _lastBucket.get();
    }
    
    /**
     * Increments the count in the current sample interval.  If the current
     * interval has exceeded, assigns the current count to the
     * last bucket and zeros out the current bucket
     */
    public void increment() {
        /*
        long now = System.currentTimeMillis();
        if(_threshold < now) {
            _lastBucket.set(_currentBucket.get());
            _currentBucket.set(0);
            _threshold = now + _sampleInterval;
        }
        */
        _currentBucket.incrementAndGet();
    }
}

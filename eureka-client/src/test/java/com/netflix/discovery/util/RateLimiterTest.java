/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.util;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class RateLimiterTest {

    private static final long START = 1000000;
    private static final int BURST_SIZE = 2;
    private static final int AVERAGE_RATE = 10;

    @Test
    public void testEvenLoad() {
        RateLimiter secondLimiter = new RateLimiter(TimeUnit.SECONDS);
        long secondStep = 1000 / AVERAGE_RATE;
        testEvenLoad(secondLimiter, START, BURST_SIZE, AVERAGE_RATE, secondStep);

        RateLimiter minuteLimiter = new RateLimiter(TimeUnit.MINUTES);
        long minuteStep = 60 * 1000 / AVERAGE_RATE;
        testEvenLoad(minuteLimiter, START, BURST_SIZE, AVERAGE_RATE, minuteStep);
    }

    private void testEvenLoad(RateLimiter rateLimiter, long start, int burstSize, int averageRate, long step) {
        for (long currentTime = start; currentTime < 3; currentTime += step) {
            assertTrue(rateLimiter.acquire(burstSize, averageRate, currentTime));
        }
    }

    @Test
    public void testBursts() {
        RateLimiter secondLimiter = new RateLimiter(TimeUnit.SECONDS);
        long secondStep = 1000 / AVERAGE_RATE;
        testBursts(secondLimiter, START, BURST_SIZE, AVERAGE_RATE, secondStep);

        RateLimiter minuteLimiter = new RateLimiter(TimeUnit.MINUTES);
        long minuteStep = 60 * 1000 / AVERAGE_RATE;
        testBursts(minuteLimiter, START, BURST_SIZE, AVERAGE_RATE, minuteStep);
    }

    private void testBursts(RateLimiter rateLimiter, long start, int burstSize, int averageRate, long step) {
        // Generate burst, and go above the limit
        assertTrue(rateLimiter.acquire(burstSize, averageRate, start));
        assertTrue(rateLimiter.acquire(burstSize, averageRate, start));
        assertFalse(rateLimiter.acquire(burstSize, averageRate, start));

        // Now advance by 1.5 STEP
        assertTrue(rateLimiter.acquire(burstSize, averageRate, start + step + step / 2));
        assertFalse(rateLimiter.acquire(burstSize, averageRate, start + step + step / 2));
        assertTrue(rateLimiter.acquire(burstSize, averageRate, start + 2 * step));
    }
}
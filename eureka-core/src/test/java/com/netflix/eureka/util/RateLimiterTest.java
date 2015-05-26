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

package com.netflix.eureka.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class RateLimiterTest {

    private static final long START = 1000000;
    private static final int BURST_SIZE = 2;
    private static final int AVERAGE_RATE = 10;
    private static final long STEP = 1000 / AVERAGE_RATE;

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    public void testEvenLoad() throws Exception {
        for (long currentTime = START; currentTime < 3; currentTime += STEP) {
            assertTrue(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, currentTime));
        }
    }

    @Test
    public void testBursts() throws Exception {
        // Generate burst, and go above the limit
        assertTrue(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, START));
        assertTrue(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, START));
        assertFalse(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, START));

        // Now advance by 1.5 STEP
        assertTrue(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, START + STEP + STEP / 2));
        assertFalse(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, START + STEP + STEP / 2));
        assertTrue(rateLimiter.acquire(BURST_SIZE, AVERAGE_RATE, START + 2 * STEP));
    }
}
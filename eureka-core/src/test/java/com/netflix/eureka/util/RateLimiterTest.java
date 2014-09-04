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

import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class RateLimiterTest {

    private RateLimiter rateLimiter = new RateLimiter();
    private int maxInWindow = 2;
    private int windowSize = 10;

    @Test
    public void testEvenLoad() throws Exception {
        long step = windowSize / maxInWindow;
        for (long currentTime = 0; currentTime < 3 * windowSize; currentTime += step) {
            assertTrue(rateLimiter.check(maxInWindow, windowSize, currentTime));
        }
    }

    @Test
    public void testBursts() throws Exception {
        long startTime = 0;

        // Generate burst, and go above the limit
        assertTrue(rateLimiter.check(maxInWindow, windowSize, startTime));
        assertTrue(rateLimiter.check(maxInWindow, windowSize, startTime));
        assertFalse(rateLimiter.check(maxInWindow, windowSize, startTime));
    }
}
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

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Adaptation of rate limiter implementation from astyanax project:
 * <a href="https://github.com/Netflix/astyanax/blob/master/astyanax-core/src/main/java/com/netflix/astyanax/connectionpool/impl/SimpleRateLimiterImpl.java">SimpleRateLimiterImpl</a>.
 */
public class RateLimiter {

    private final ConcurrentLinkedDeque<Long> queue = new ConcurrentLinkedDeque<Long>();
    private final ReentrantLock lock = new ReentrantLock();

    public boolean check(int maxInWindow, int windowSize) {
        return check(maxInWindow, windowSize, System.currentTimeMillis());
    }

    public boolean check(int maxInWindow, int windowSize, long currentTimeMillis) {
        if (maxInWindow == 0) {
            return true;
        }

        // Haven't reached the count limit yet. It is possible that we actually go slightly above
        // the limit (concurrency), but it is ok.
        if (queue.size() < maxInWindow) {
            queue.addFirst(currentTimeMillis);
            return true;
        } else {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                Long last = queue.peekLast();
                if (last == null) { // Possible, since the critical section established after if statement
                    queue.addFirst(currentTimeMillis);
                    return true;
                }
                if (currentTimeMillis - last < windowSize) {
                    return false;
                }
                queue.addFirst(currentTimeMillis);
                queue.removeLast();
                return true;
            } finally {
                lock.unlock();
            }
        }
    }

    public void reset() {
        queue.clear();
    }
}
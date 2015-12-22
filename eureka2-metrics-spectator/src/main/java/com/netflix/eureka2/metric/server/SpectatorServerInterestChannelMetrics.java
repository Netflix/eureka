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

package com.netflix.eureka2.metric.server;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.metric.SpectatorEurekaMetrics;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
public class SpectatorServerInterestChannelMetrics extends SpectatorEurekaMetrics implements ServerInterestChannelMetrics {

    /**
     * We could use {@link java.util.concurrent.ConcurrentHashMap} with putIfAbsent method
     * to eliminate locking, if creating multiple instances of the same counter wouldn't be
     * a problem.
     */
    private final Map<InterestKey, AtomicInteger> subscribedClientsByInterest = new HashMap<>();
    private final Map<String, Counter> notificationCountersByApplication = new HashMap<>();

    private final AtomicInteger interestAllCounter = new AtomicInteger();
    private final AtomicInteger totalInstanceInterests = new AtomicInteger();

    public SpectatorServerInterestChannelMetrics(ExtendedRegistry registry) {
        super(registry, "server");
        newGauge("interestAll", interestAllCounter);
        newGauge("totalInstanceInterests", totalInstanceInterests);
    }

    @Override
    public void incrementApplicationNotificationCounter(String applicationName) {
        getApplicationNotificationCounter(applicationName).increment();
    }

    @Override
    public void incrementSubscriptionCounter(AtomicInterest interestType, String id) {
        if (interestType == AtomicInterest.Instance) {
            totalInstanceInterests.incrementAndGet();
        }
        if (interestType == AtomicInterest.InterestAll) {
            interestAllCounter.incrementAndGet();
        } else {
            getSubscriptionCounter(new InterestKey(interestType, id)).incrementAndGet();
        }
    }

    public void decrementSubscriptionCounter(AtomicInterest interestType, String id) {
        if (interestType == AtomicInterest.Instance) {
            totalInstanceInterests.decrementAndGet();
        }
        if (interestType == AtomicInterest.InterestAll) {
            interestAllCounter.decrementAndGet();
        } else {
            getSubscriptionCounter(new InterestKey(interestType, id)).decrementAndGet();
        }
    }

    private AtomicInteger getSubscriptionCounter(InterestKey interestKey) {
        AtomicInteger counter = subscribedClientsByInterest.get(interestKey);
        if (counter == null) {
            synchronized (subscribedClientsByInterest) {
                counter = subscribedClientsByInterest.get(interestKey);
                if (counter == null) {
                    counter = new AtomicInteger();
                    newGauge("subscriptions." + interestKey.getCounterName(), counter);
                    subscribedClientsByInterest.put(interestKey, counter);
                }
            }
        }
        return counter;
    }

    private Counter getApplicationNotificationCounter(String applicationName) {
        String counterName = "notifications." + applicationName;
        Counter counter = notificationCountersByApplication.get(counterName);
        if (counter == null) {
            synchronized (notificationCountersByApplication) {
                counter = notificationCountersByApplication.get(counterName);
                if (counter == null) {
                    counter = newCounter(counterName);
                    notificationCountersByApplication.put(counterName, counter);
                }
            }
        }
        return counter;
    }

    private static class InterestKey {
        private final AtomicInterest type;
        private final String id;

        private InterestKey(AtomicInterest type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            InterestKey that = (InterestKey) o;

            if (id != null ? !id.equals(that.id) : that.id != null) {
                return false;
            }
            if (type != that.type) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = type != null ? type.hashCode() : 0;
            result = 31 * result + (id != null ? id.hashCode() : 0);
            return result;
        }

        public String getCounterName() {
            return Character.toLowerCase(type.name().charAt(0)) + type.name().substring(1) + '.' + id;
        }
    }
}

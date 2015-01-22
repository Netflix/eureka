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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.channel.InterestChannel.STATE;
import com.netflix.eureka2.interests.ApplicationInterest;
import com.netflix.eureka2.interests.FullRegistryInterest;
import com.netflix.eureka2.interests.InstanceInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.VipInterest;
import com.netflix.eureka2.metric.AbstractStateMachineMetrics;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.spectator.api.ExtendedRegistry;

/**
 * @author Tomasz Bak
 */
public class SpectatorServerInterestChannelMetrics extends AbstractStateMachineMetrics<STATE> implements ServerInterestChannelMetrics {

    /**
     * We could use {@link java.util.concurrent.ConcurrentHashMap} with putIfAbsent method
     * to eliminate locking, if creating multiple instances of the same counter wouldn't be
     * a problem.
     */
//    private final Map<InterestKey, LongGauge> subscribedClientsByInterest = new HashMap<>();
//    private final Map<String, Counter> notificationCountersByApplication = new HashMap<>();
//
//    private final LongGauge interestAllCounter;
//    private final LongGauge totalInstanceInterests;
    public SpectatorServerInterestChannelMetrics(ExtendedRegistry registry) {
        super(registry, "server", STATE.class);
//        this.interestAllCounter = newLongGauge("interestAll");
//        this.totalInstanceInterests = newLongGauge("totalInstanceInterests");
    }

    public void incrementApplicationNotificationCounter(String applicationName) {
//        getApplicationNotificationCounter(applicationName).increment();
    }

    public void incrementSubscriptionCounter(AtomicInterest interestType, String id) {
//        if (interestType == AtomicInterest.Instance) {
//            ServoUtils.incrementLongGauge(totalInstanceInterests);
//        }
//        if (interestType == AtomicInterest.InterestAll) {
//            ServoUtils.incrementLongGauge(interestAllCounter);
//        } else {
//            ServoUtils.incrementLongGauge(getSubscriptionCounter(new InterestKey(interestType, id)));
//        }
    }

    public void decrementSubscriptionCounter(AtomicInterest interestType, String id) {
//        if (interestType == AtomicInterest.Instance) {
//            ServoUtils.decrementLongGauge(totalInstanceInterests);
//        }
//        if (interestType == AtomicInterest.InterestAll) {
//            ServoUtils.decrementLongGauge(interestAllCounter);
//        } else {
//            ServoUtils.decrementLongGauge(getSubscriptionCounter(new InterestKey(interestType, id)));
//        }
    }

//    private LongGauge getSubscriptionCounter(InterestKey interestKey) {
//        LongGauge counter = subscribedClientsByInterest.get(interestKey);
//        if (counter == null) {
//            synchronized (subscribedClientsByInterest) {
//                counter = subscribedClientsByInterest.get(interestKey);
//                if (counter == null) {
//                    counter = new LongGauge(monitorConfig("subscriptions." + interestKey.getCounterName()));
//                    subscribedClientsByInterest.put(interestKey, counter);
//                    register(counter);
//                }
//            }
//        }
//        return counter;
//    }
//
//    private Counter getApplicationNotificationCounter(String applicationName) {
//        String counterName = "notifications." + applicationName;
//        Counter counter = notificationCountersByApplication.get(counterName);
//        if (counter == null) {
//            synchronized (notificationCountersByApplication) {
//                counter = notificationCountersByApplication.get(counterName);
//                if (counter == null) {
//                    counter = new BasicCounter(monitorConfig(counterName));
//                    notificationCountersByApplication.put(counterName, counter);
//                    register(counter);
//                }
//            }
//        }
//        return counter;
//    }



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

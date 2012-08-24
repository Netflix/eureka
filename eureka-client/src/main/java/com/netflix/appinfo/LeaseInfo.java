/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.appinfo;

import com.thoughtworks.xstream.annotations.XStreamOmitField;

/**
 * Represents the <em>lease</em> information with <em>Eureka</em>.
 * 
 * <p>
 * <em>Eureka</em> decides to remove the instance out of its view depending on
 * the duration that is set in
 * {@link EurekaInstanceConfig#getLeaseExpirationDurationInSeconds()} which is
 * held in this lease. The lease also tracks the last time it was renewed.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class LeaseInfo {

    public static final class Builder {

        @XStreamOmitField
        private LeaseInfo result;

        private Builder() {
            result = new LeaseInfo();
        }

        public static Builder newBuilder() {
            return new Builder();
        }

        /**
         * Sets the registration timestamp
         */
        public Builder setRegistrationTimestamp(long ts) {
            result.registrationTimestamp = ts;
            return this;
        }

        /**
         * Sets the last renewal timestamp of lease
         */
        public Builder setRenewalTimestamp(long ts) {
            result.lastRenewalTimestamp = ts;
            return this;
        }

        /**
         * Sets the de-registration timestamp
         */
        public Builder setEvictionTimestamp(long ts) {
            result.evictionTimestamp = ts;
            return this;
        }

        /**
         * Sets the client specified setting for eviction (e.g. how long to wait
         * w/o renewal event)
         */
        public Builder setDurationInSecs(int d) {
            if (d <= 0) {
                result.durationInSecs = DEFAULT_LEASE_DURATION;
            } else {
                result.durationInSecs = d;
            }
            return this;
        }

        /**
         * Sets the client specified setting for renew interval
         */
        public Builder setRenewalIntervalInSecs(int i) {
            if (i <= 0) {
                result.renewalIntervalInSecs = DEFAULT_LEASE_RENEWAL_INTERVAL;
            } else {
                result.renewalIntervalInSecs = i;
            }
            return this;
        }

        /**
         * Sets the logical clock
         */
        public Builder setClock(long clock) {
            result.clock = clock;
            return this;
        }

        /**
         * Build the {@link InstanceInfo}
         */
        public LeaseInfo build() {
            return result;
        }
    }

    public static final int DEFAULT_LEASE_RENEWAL_INTERVAL = 30;
    public static final int DEFAULT_LEASE_DURATION = 90;

    // Client settings
    private int renewalIntervalInSecs = DEFAULT_LEASE_RENEWAL_INTERVAL;
    private int durationInSecs = DEFAULT_LEASE_DURATION;

    // Server populated
    private long registrationTimestamp;
    private long lastRenewalTimestamp;
    private long evictionTimestamp;
    private long clock;

    private LeaseInfo() {
    }

    /**
     * Returns the registration timestamp
     */
    public long getRegistrationTimestamp() {
        return registrationTimestamp;
    }

    /**
     * Returns the last renewal timestamp of lease
     */
    public long getRenewalTimestamp() {
        return lastRenewalTimestamp;
    }

    /**
     * Returns the de-registration timestamp
     */
    public long getEvictionTimestamp() {
        return evictionTimestamp;
    }

    /**
     * Returns client specified setting for renew interval
     */
    public int getRenewalIntervalInSecs() {
        return renewalIntervalInSecs;
    }

    /**
     * Returns client specified setting for eviction (e.g. how long to wait w/o
     * renewal event)
     */
    public int getDurationInSecs() {
        return durationInSecs;
    }

    /**
     * Returns the logical clock
     */
    public long getClock() {
        return clock;
    }

}
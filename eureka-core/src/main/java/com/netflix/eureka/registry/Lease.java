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

package com.netflix.eureka.registry;

import com.netflix.eureka.interests.ChangeNotification;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Represent a lease over an element E
 * This object should be thread safe
 * TODO: figure out a way to sync time between write servers (or even if necessary)
 * @author David Liu
 */
public class Lease<E> {

    /**
     * Each lease entry in a registry is associated with exactly one origin:
     * <ul>
     * <li>{@link #ATTACHED_CLIENT}</li> - there is an opened registration client connection to the write local server
     * <li>{@link #REPLICATED}</li> - replicated entry from another server
     * </ul>
     */
    public enum Origin { ATTACHED_CLIENT, REPLICATED }

    @Deprecated
    public static final int DEFAULT_LEASE_DURATION_MILLIS = 90 * 1000;  // TODO: get default via config

    private E holder;
    private ChangeNotification<E> snapshot;

    private Origin origin;

    @Deprecated
    private final AtomicLong lastRenewalTimestamp;  // timestamp of last renewal

    @Deprecated
    private final AtomicLong leaseDurationMillis;  // duration of this lease in millis

    public Lease(E holder) {
        this(holder, DEFAULT_LEASE_DURATION_MILLIS);
    }

    public Lease(E holder, long durationMillis) {
        set(holder);

        lastRenewalTimestamp = new AtomicLong(System.currentTimeMillis());
        leaseDurationMillis = new AtomicLong(durationMillis);
    }

    public synchronized void set(E holder) {
        this.holder = holder;
        snapshot = new ChangeNotification<>(ChangeNotification.Kind.Add, holder);
    }

    public E getHolder() {
        return holder;
    }

    public ChangeNotification<E> getHolderSnapshot() {
        return snapshot;
    }

    public long getLastRenewalTimestamp() {
        return lastRenewalTimestamp.get();
    }

    public long getLeaseDurationMillis() {
        return leaseDurationMillis.get();
    }

    public void renew() {
        lastRenewalTimestamp.set(System.currentTimeMillis());
    }

    public void renew(long durationMillis) {
        leaseDurationMillis.set(durationMillis);
        renew();
    }

    public boolean hasExpired() {
        return System.currentTimeMillis() > (lastRenewalTimestamp.get() + leaseDurationMillis.get());
    }

    public void cancel() {
        // TODO necessary?
    }

    @Override
    public String toString() {
        return "Lease{" +
                "holder=" + holder +
                ", snapshot=" + snapshot +
                ", lastRenewalTimestamp=" + lastRenewalTimestamp +
                ", leaseDurationMillis=" + leaseDurationMillis +
                '}';
    }
}

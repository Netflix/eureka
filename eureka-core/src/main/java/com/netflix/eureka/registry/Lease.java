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

/**
 * Represent a lease over an element E
 * This object should be thread safe
 *
 * @author David Liu
 */
public class Lease<E> {

    /**
     * Each lease entry in a registry is associated with exactly one origin:
     * <ul>
     * <li>{@link #LOCAL}</li> - there is an opened registration client connection to the write local server
     * <li>{@link #REPLICATED}</li> - replicated entry from another server
     * </ul>
     */
    public enum Origin { LOCAL, REPLICATED }

    private E holder;
    private ChangeNotification<E> snapshot;

    private Origin origin;

    public Lease(E holder) {
        this.holder = holder;
        snapshot = new ChangeNotification<>(ChangeNotification.Kind.Add, holder);
    }

    public E getHolder() {
        return holder;
    }

    public ChangeNotification<E> getHolderSnapshot() {
        return snapshot;
    }

    @Override
    public String toString() {
        return "Lease{" +
                "holder=" + holder +
                ", snapshot=" + snapshot +
                ", origin=" + origin +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lease)) return false;

        Lease lease = (Lease) o;

        if (holder != null ? !holder.equals(lease.holder) : lease.holder != null) return false;
        if (origin != lease.origin) return false;
        if (snapshot != null ? !snapshot.equals(lease.snapshot) : lease.snapshot != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = holder != null ? holder.hashCode() : 0;
        result = 31 * result + (snapshot != null ? snapshot.hashCode() : 0);
        result = 31 * result + (origin != null ? origin.hashCode() : 0);
        return result;
    }
}

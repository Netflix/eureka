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

package com.netflix.eureka.interests;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Eureka client can subscribe to multiple interests at the same time. This class
 * describes such multiple interests subscription.
 *
 * @author Tomasz Bak
 */
public class MultipleInterests<T> extends Interest<T> {

    private final Set<Interest<T>> interests;

    public MultipleInterests() {
        this.interests = new HashSet<>();
    }

    public MultipleInterests(Interest<T>... interests) {
        this.interests = new HashSet<>();
        Collections.addAll(this.interests, interests);
    }

    public Collection<Interest<T>> getInterests() {
        return interests;
    }

    public synchronized void clear() {
        interests.clear();
    }

    public synchronized void add(Interest<T> interest) {
        interests.add(interest);
        flatten();
    }

    @Override
    public boolean matches(T data) {
        for (Interest<T> interest : interests) {
            if (interest.matches(data)) {
                return true;
            }
        }
        return false;
    }

    // TODO: properly deal with merging subsets into supersets
    public Set<Interest<T>> flatten() {
        Set<Interest<T>> set = new HashSet<>();
        flatten(this, set);
        return set;
    }

    private void flatten(Interest<T> interest, Set<Interest<T>> collector) {
        if (interest instanceof MultipleInterests) {
            for (Interest<T> i : ((MultipleInterests<T>) interest).getInterests()) {
                flatten(i, collector);
            }
        } else {
            collector.add(interest);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MultipleInterests)) return false;

        MultipleInterests that = (MultipleInterests) o;

        if ( !interests.equals(that.interests) ) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return interests.hashCode();
    }

    @Override
    public String toString() {
        return "MultipleInterests{" +
                "interests=" + interests +
                "} " + super.toString();
    }
}

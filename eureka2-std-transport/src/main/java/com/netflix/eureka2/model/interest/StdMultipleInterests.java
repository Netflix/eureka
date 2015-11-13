/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.model.interest;

import java.util.HashSet;
import java.util.Set;

/**
 */
public class StdMultipleInterests<T> implements MultipleInterests<T> {
    private final Set<Interest<T>> interests;

    @SafeVarargs
    public StdMultipleInterests(Interest<T>... interests) {
        this.interests = new HashSet<>();
        for (Interest<T> interest : interests) {
            append(interest, this.interests); // Unwraps if the interest itself is a MultipleInterest
        }
    }

    public StdMultipleInterests(Iterable<Interest<T>> interests) {
        this.interests = new HashSet<>();
        for (Interest<T> interest : interests) {
            append(interest, this.interests); // Unwraps if the interest itself is a MultipleInterest
        }
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Composite;
    }

    @Override
    public Operator getOperator() {
        return null;
    }

    @Override
    public String getPattern() {
        return null;
    }

    @Override
    public Set<Interest<T>> getInterests() {
        return interests;
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

    @Override
    public boolean isAtomicInterest() {
        return false;
    }

    @Override
    public Set<Interest<T>> flatten() {
        return new HashSet<>(interests); // Copy to restrict mutations to the underlying set.
    }

    @Override
    public MultipleInterests<T> copyAndAppend(Interest<T> toAppend) {
        Set<Interest<T>> newInterests = flatten(); // flatten does the copy of the underlying set
        append(toAppend, newInterests);
        return new StdMultipleInterests<>(newInterests);
    }

    @Override
    public MultipleInterests<T> copyAndRemove(Interest<T> toAppend) {
        Set<Interest<T>> newInterests = flatten(); // flatten does the copy of the underlying set
        remove(toAppend, newInterests);
        return new StdMultipleInterests<>(newInterests);
    }

    private static <T> void append(Interest<T> interest, Set<Interest<T>> collector) {
        if (interest instanceof MultipleInterests) {
            for (Interest<T> i : ((MultipleInterests<T>) interest).getInterests()) {
                append(i, collector);
            }
        } else {
            collector.add(interest);
        }
    }

    private static <T> void remove(Interest<T> interest, Set<Interest<T>> collector) {
        if (interest instanceof MultipleInterests) {
            for (Interest<T> i : ((MultipleInterests<T>) interest).getInterests()) {
                remove(i, collector);
            }
        } else {
            collector.remove(interest);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StdMultipleInterests)) {
            return false;
        }

        StdMultipleInterests that = (StdMultipleInterests) o;

        return interests.equals(that.interests);

    }

    @Override
    public int hashCode() {
        return interests.hashCode();
    }

    @Override
    public String toString() {
        return "MultipleInterests{" + "interests=" + interests + '}';
    }
}

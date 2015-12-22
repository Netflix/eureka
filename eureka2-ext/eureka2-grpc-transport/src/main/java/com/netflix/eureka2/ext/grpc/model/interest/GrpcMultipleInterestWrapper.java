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

package com.netflix.eureka2.ext.grpc.model.interest;

import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;

import java.util.HashSet;
import java.util.Set;

/**
 */
public class GrpcMultipleInterestWrapper extends GrpcInterestWrapper implements MultipleInterests<InstanceInfo> {

    private final Set<Interest<InstanceInfo>> interestWrappers;

    public GrpcMultipleInterestWrapper(Interest<InstanceInfo>... interestWrappers) {
        this.interestWrappers = new HashSet<>();
        for (Interest<InstanceInfo> interest : interestWrappers) {
            append(interest, this.interestWrappers); // Unwraps if the interest itself is a MultipleInterest
        }
    }

    public GrpcMultipleInterestWrapper(Set<Interest<InstanceInfo>> interestWrappers) {
        this.interestWrappers = interestWrappers;
    }

    @Override
    public Eureka2.GrpcInterest getGrpcObject() {
        throw new IllegalStateException("Interest type not handled by Grpc layer");
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
    public boolean matches(InstanceInfo data) {
        for (Interest<InstanceInfo> interest : interestWrappers) {
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
    public Set<Interest<InstanceInfo>> getInterests() {
        return interestWrappers;
    }

    @Override
    public Set<Interest<InstanceInfo>> flatten() {
        return new HashSet<>(interestWrappers); // Copy to restrict mutations to the underlying set.
    }

    @Override
    public MultipleInterests<InstanceInfo> copyAndAppend(Interest<InstanceInfo> toAppend) {
        Set<Interest<InstanceInfo>> newInterests = flatten(); // flatten does the copy of the underlying set
        append(toAppend, newInterests);
        return new GrpcMultipleInterestWrapper(newInterests);
    }

    @Override
    public MultipleInterests<InstanceInfo> copyAndRemove(Interest<InstanceInfo> toAppend) {
        Set<Interest<InstanceInfo>> newInterests = flatten(); // flatten does the copy of the underlying set
        remove(toAppend, newInterests);
        return new GrpcMultipleInterestWrapper(newInterests);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GrpcMultipleInterestWrapper that = (GrpcMultipleInterestWrapper) o;

        return interestWrappers != null ? interestWrappers.equals(that.interestWrappers) : that.interestWrappers == null;

    }

    @Override
    public int hashCode() {
        return interestWrappers != null ? interestWrappers.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "GrpcMultipleInterestWrapper{interestWrappers=" + interestWrappers + '}';
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
}

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

package com.netflix.eureka2.protocol.interest;

import java.util.Arrays;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.interest.MultipleInterests;
import com.netflix.eureka2.model.interest.StdMultipleInterests;
import com.netflix.eureka2.spi.protocol.interest.InterestRegistration;

/**
 */
public class StdInterestRegistration implements InterestRegistration {

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final Interest<InstanceInfo>[] interests;

    public StdInterestRegistration() {
        interests = null;
    }

    public StdInterestRegistration(Interest<InstanceInfo> interest) {
        if (interest instanceof MultipleInterests) {
            Set<Interest<InstanceInfo>> set = ((MultipleInterests<InstanceInfo>) interest).flatten();
            interests = set.toArray(new Interest[set.size()]);
        } else {
            interests = new Interest[]{interest};
        }
    }

    public Interest<InstanceInfo>[] getInterests() {
        return interests;
    }

    public Interest<InstanceInfo> toComposite() {
        if (interests.length == 0) {
            return Interests.forNone();
        }
        if (interests.length > 1) {
            return new StdMultipleInterests<InstanceInfo>(interests);
        }
        return interests[0];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StdInterestRegistration that = (StdInterestRegistration) o;

        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(interests, that.interests);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(interests);
    }

    @Override
    public String toString() {
        return "StdInterestRegistration{interests=" + Arrays.toString(interests) + '}';
    }
}

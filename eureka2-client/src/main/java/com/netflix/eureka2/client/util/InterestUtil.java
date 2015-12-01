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

package com.netflix.eureka2.client.util;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;

/**
 */
public final class InterestUtil {

    private InterestUtil() {
    }

    public static boolean isEmptyInterest(Interest<InstanceInfo> interest) {
        if (interest.getQueryType() == Interest.QueryType.None) {
            return true;
        }
        if (interest instanceof MultipleInterests) {
            MultipleInterests<InstanceInfo> multiple = (MultipleInterests<InstanceInfo>) interest;
            if (multiple.flatten().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}

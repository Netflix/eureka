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

package com.netflix.eureka2.model;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interest.Operator;
import com.netflix.eureka2.model.interest.MultipleInterests;

/**
 */
public abstract class InterestModel {

    private static volatile InterestModel defaultModel;

    public abstract Interest<InstanceInfo> newEmptyRegistryInterest();

    public abstract Interest<InstanceInfo> newFullRegistryInterest();

    public abstract Interest<InstanceInfo> newApplicationInterest(String applicationName, Operator operator);

    public abstract Interest<InstanceInfo> newVipInterest(String vip, Operator operator);

    public abstract Interest<InstanceInfo> newSecureVipInterest(String secureVip, Operator operator);

    public abstract Interest<InstanceInfo> newInstanceInterest(String instanceId, Operator operator);

    public abstract <T> MultipleInterests<T> newMultipleInterests(Interest<T>... interests);

    public static InterestModel getDefaultModel() {
        return defaultModel;
    }

    public static InterestModel setDefaultModel(InterestModel newModel) {
        InterestModel previous = defaultModel;
        defaultModel = newModel;
        return previous;
    }
}

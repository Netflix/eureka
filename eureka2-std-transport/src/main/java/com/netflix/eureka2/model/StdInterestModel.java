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
import com.netflix.eureka2.model.interest.StdApplicationInterest;
import com.netflix.eureka2.model.interest.StdEmptyRegistryInterest;
import com.netflix.eureka2.model.interest.StdFullRegistryInterest;
import com.netflix.eureka2.model.interest.StdInstanceInterest;
import com.netflix.eureka2.model.interest.StdMultipleInterests;
import com.netflix.eureka2.model.interest.StdSecureVipInterest;
import com.netflix.eureka2.model.interest.StdVipInterest;

/**
 */
public class StdInterestModel extends InterestModel {

    private static final StdInterestModel INSTANCE = new StdInterestModel();

    public static StdInterestModel getStdModel() {
        return INSTANCE;
    }

    @Override
    public Interest<InstanceInfo> newEmptyRegistryInterest() {
        return StdEmptyRegistryInterest.getInstance();
    }

    @Override
    public Interest<InstanceInfo> newFullRegistryInterest() {
        return StdFullRegistryInterest.getInstance();
    }

    @Override
    public Interest<InstanceInfo> newApplicationInterest(String applicationName, Operator operator) {
        return new StdApplicationInterest(applicationName, operator);
    }

    @Override
    public Interest<InstanceInfo> newVipInterest(String vip, Operator operator) {
        return new StdVipInterest(vip, operator);
    }

    @Override
    public Interest<InstanceInfo> newSecureVipInterest(String secureVip, Operator operator) {
        return new StdSecureVipInterest(secureVip, operator);
    }

    @Override
    public Interest<InstanceInfo> newInstanceInterest(String instanceId, Operator operator) {
        return new StdInstanceInterest(instanceId, operator);
    }

    @Override
    public <T> MultipleInterests<T> newMultipleInterests(Interest<T>... interests) {
        return new StdMultipleInterests<>(interests);
    }
}

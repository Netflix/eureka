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

package com.netflix.eureka2.ext.grpc.model;

import com.netflix.eureka2.ext.grpc.model.interest.*;
import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;

/**
 */
public class GrpcInterestModel extends InterestModel {

    private static final GrpcInterestModel INSTANCE = new GrpcInterestModel();

    @Override
    public Interest<InstanceInfo> newEmptyRegistryInterest() {
        return GrpcEmptyRegistryInterestWrapper.getInstance();
    }

    @Override
    public Interest<InstanceInfo> newFullRegistryInterest() {
        return GrpcFullRegistryInterestWrapper.getInstance();
    }

    @Override
    public Interest<InstanceInfo> newApplicationInterest(String applicationName, Interest.Operator operator) {
        checkNotNull(applicationName, operator);
        return new GrpcApplicationInterestWrapper(applicationName, operator);
    }

    @Override
    public Interest<InstanceInfo> newVipInterest(String vip, Interest.Operator operator) {
        checkNotNull(vip, operator);
        return new GrpcVipInterestWrapper(vip, operator);
    }

    @Override
    public Interest<InstanceInfo> newSecureVipInterest(String secureVip, Interest.Operator operator) {
        checkNotNull(secureVip, operator);
        return new GrpcSecureVipInterestWrapper(secureVip, operator);
    }

    @Override
    public Interest<InstanceInfo> newInstanceInterest(String instanceId, Interest.Operator operator) {
        return null;
    }

    @Override
    public MultipleInterests<InstanceInfo> newMultipleInterests(Interest<InstanceInfo>... interests) {
        return new GrpcMultipleInterestWrapper(interests);
    }

    public static GrpcInterestModel getGrpcModel() {
        return INSTANCE;
    }

    static void checkNotNull(String pattern, Interest.Operator operator) {
        if (pattern == null) {
            throw new IllegalArgumentException("Expected non null pattern value");
        }
        if (operator == null) {
            throw new IllegalArgumentException("Expected non null operator value");
        }
    }
}

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

import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest.Operator;

/**
 * A factory to create instances of {@link Interest}.
 *
 * @author Nitesh Kant
 */
public final class Interests {

    private Interests() {
    }

    public static Interest<InstanceInfo> forVips(String... vips) {
        return forVips(Operator.Equals, vips);
    }

    public static Interest<InstanceInfo> forVips(Operator operator, String... vips) {
        if (vips.length == 0) {
            return InterestModel.getDefaultModel().newEmptyRegistryInterest();
        }
        if (vips.length == 1)
            return InterestModel.getDefaultModel().newVipInterest(vips[0], operator);
        Interest[] interests = new Interest[vips.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = InterestModel.getDefaultModel().newVipInterest(vips[i], operator);
        }
        return InterestModel.getDefaultModel().newMultipleInterests(interests);
    }

    public static Interest<InstanceInfo> forSecureVips(String... secureVips) {
        return forSecureVips(Operator.Equals, secureVips);
    }

    public static Interest<InstanceInfo> forSecureVips(Operator operator, String... secureVips) {
        if (secureVips.length == 0) {
            return InterestModel.getDefaultModel().newEmptyRegistryInterest();
        }
        if (secureVips.length == 1) {
            return InterestModel.getDefaultModel().newSecureVipInterest(secureVips[0], operator);
        }
        Interest[] interests = new Interest[secureVips.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = InterestModel.getDefaultModel().newSecureVipInterest(secureVips[i], operator);
        }
        return InterestModel.getDefaultModel().newMultipleInterests(interests);
    }

    public static Interest<InstanceInfo> forApplications(String... applicationNames) {
        return forApplications(Operator.Equals, applicationNames);
    }

    public static Interest<InstanceInfo> forApplications(Operator operator, String... applicationNames) {
        if (applicationNames.length == 0) {
            return InterestModel.getDefaultModel().newEmptyRegistryInterest();
        }
        if (applicationNames.length == 1) {
            return InterestModel.getDefaultModel().newApplicationInterest(applicationNames[0], operator);
        }
        Interest[] interests = new Interest[applicationNames.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = InterestModel.getDefaultModel().newApplicationInterest(applicationNames[i], operator);
        }
        return InterestModel.getDefaultModel().newMultipleInterests(interests);
    }

    public static Interest<InstanceInfo> forInstances(String... instanceIds) {
        return forInstance(Operator.Equals, instanceIds);
    }

    public static Interest<InstanceInfo> forInstance(Operator operator, String... instanceIds) {
        if (instanceIds.length == 0) {
            return InterestModel.getDefaultModel().newEmptyRegistryInterest();
        }
        if (instanceIds.length == 1) {
            return InterestModel.getDefaultModel().newInstanceInterest(instanceIds[0], operator);
        }
        Interest[] interests = new Interest[instanceIds.length];
        for (int i = 0; i < interests.length; i++) {
            interests[i] = InterestModel.getDefaultModel().newInstanceInterest(instanceIds[i], operator);
        }
        return InterestModel.getDefaultModel().newMultipleInterests(interests);
    }

    public static Interest<InstanceInfo> forFullRegistry() {
        return InterestModel.getDefaultModel().newFullRegistryInterest();
    }

    public static Interest<InstanceInfo> forNone() {
        return InterestModel.getDefaultModel().newEmptyRegistryInterest();
    }

    @SafeVarargs
    public static Interest<InstanceInfo> forSome(Interest<InstanceInfo>... interests) {
        return InterestModel.getDefaultModel().newMultipleInterests(interests);
    }
}

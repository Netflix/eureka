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

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;

/**
 */
public abstract class GrpcInterestWrapper implements Interest<InstanceInfo>, GrpcObjectWrapper<Eureka2.GrpcInterest> {

    public static Operator toOpertor(Eureka2.GrpcInterest.GrpcInterestOperator grpcOperator) {
        if (grpcOperator == null) {
            return null;
        }
        switch (grpcOperator) {
            case Equal:
                return Operator.Equals;
            case Like:
                return Operator.Like;
        }
        throw new IllegalArgumentException("Unrecognized operator " + grpcOperator);
    }

    public static Eureka2.GrpcInterest.GrpcInterestOperator toGrpcOpertor(Operator operator) {
        if (operator == null) {
            return null;
        }
        switch (operator) {
            case Equals:
                return Eureka2.GrpcInterest.GrpcInterestOperator.Equal;
            case Like:
                return Eureka2.GrpcInterest.GrpcInterestOperator.Like;
        }
        return Eureka2.GrpcInterest.GrpcInterestOperator.UNRECOGNIZED;
    }

    public static <G> GrpcInterestWrapper toInterest(Eureka2.GrpcInterest grpcInterest) {
        switch (grpcInterest.getInterestOneofCase()) {
            case ALL:
                return GrpcFullRegistryInterestWrapper.getInstance();
            case NONE:
                return GrpcEmptyRegistryInterestWrapper.getInstance();
            case APPLICATION:
                return GrpcApplicationInterestWrapper.getInstance(grpcInterest);
            case VIP:
                return GrpcVipInterestWrapper.getInstance(grpcInterest);
            case SECUREVIP:
                return GrpcSecureVipInterestWrapper.getInstance(grpcInterest);
        }
        return null;
    }
}

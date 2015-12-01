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

/**
 */
public class GrpcFullRegistryInterestWrapper extends GrpcInterestWrapper {

    private static final GrpcInterestWrapper INSTANCE = new GrpcFullRegistryInterestWrapper();

    private final Eureka2.GrpcInterest grpcInterest;

    public static GrpcInterestWrapper getInstance() {
        return INSTANCE;
    }

    private GrpcFullRegistryInterestWrapper() {
        this.grpcInterest = Eureka2.GrpcInterest.newBuilder()
                .setAll(
                        Eureka2.GrpcInterest.GrpcAllInterest.newBuilder().build()
                ).build();
    }

    @Override
    public Eureka2.GrpcInterest getGrpcObject() {
        return grpcInterest;
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Any;
    }

    @Override
    public Operator getOperator() {
        return Operator.Equals;
    }

    @Override
    public String getPattern() {
        return null;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return true;
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }
}

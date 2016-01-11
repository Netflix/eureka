/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.eureka2.ext.grpc.model.transport;

import com.netflix.eureka2.ext.grpc.model.GrpcModelConverters;
import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.spi.model.transport.InterestRegistration;

/**
 */
public class GrpcInterestRegistrationWrapper implements InterestRegistration, GrpcObjectWrapper<Eureka2.GrpcInterestRegistration> {

    private final Eureka2.GrpcInterestRegistration grpcInterestRegistration;

    private volatile Interest<InstanceInfo>[] interests;

    public GrpcInterestRegistrationWrapper(Eureka2.GrpcInterestRegistration grpcInterestRegistration) {
        this.grpcInterestRegistration = grpcInterestRegistration;
    }

    @Override
    public Eureka2.GrpcInterestRegistration getGrpcObject() {
        return grpcInterestRegistration;
    }

    @Override
    public Interest<InstanceInfo>[] getInterests() {
        if (interests != null) {
            return interests;
        }
        interests = GrpcModelConverters.toAtomicInterests(grpcInterestRegistration.getInterestsList());
        return interests;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GrpcInterestRegistrationWrapper) {
            return grpcInterestRegistration.equals(((GrpcInterestRegistrationWrapper) o).getGrpcObject());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return grpcInterestRegistration.hashCode();
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcInterestRegistration);
    }

    public static InterestRegistration newInterestRegistration(Interest<InstanceInfo> interest) {
        return new GrpcInterestRegistrationWrapper(
                Eureka2.GrpcInterestRegistration.newBuilder().addAllInterests(GrpcModelConverters.toGrpcInterest(interest)).build()
        );
    }

    public static InterestRegistration asInterestRegistration(Eureka2.GrpcInterestRegistration grpcInterestRegistration) {
        return new GrpcInterestRegistrationWrapper(grpcInterestRegistration);
    }
}

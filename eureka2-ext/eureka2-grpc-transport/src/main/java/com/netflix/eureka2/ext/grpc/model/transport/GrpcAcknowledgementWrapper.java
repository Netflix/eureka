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

import com.netflix.eureka2.ext.grpc.model.GrpcObjectWrapper;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.spi.model.transport.Acknowledgement;

/**
 */
public class GrpcAcknowledgementWrapper implements Acknowledgement, GrpcObjectWrapper<Eureka2.GrpcAcknowledgement> {

    private static final GrpcAcknowledgementWrapper INSTANCE = new GrpcAcknowledgementWrapper();

    @Override
    public Eureka2.GrpcAcknowledgement getGrpcObject() {
        return Eureka2.GrpcAcknowledgement.getDefaultInstance();
    }

    @Override
    public int hashCode() {
        return 341234214;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Acknowledgement;
    }

    @Override
    public String toString() {
        return "GrpcAcknowledgementWrapper{}";
    }

    public static GrpcAcknowledgementWrapper getInstance() {
        return INSTANCE;
    }
}

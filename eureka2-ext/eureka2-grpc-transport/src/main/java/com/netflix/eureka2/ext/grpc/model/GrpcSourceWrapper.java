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

import com.netflix.eureka2.ext.grpc.util.TextPrinter;
import com.netflix.eureka2.grpc.Eureka2;
import com.netflix.eureka2.model.Source;

/**
 */
public class GrpcSourceWrapper implements Source, GrpcObjectWrapper<Eureka2.GrpcSource> {
    private final Eureka2.GrpcSource grpcSource;

    private GrpcSourceWrapper(Eureka2.GrpcSource grpcSource) {
        this.grpcSource = grpcSource;
    }

    @Override
    public Origin getOrigin() {
        return toOrigin(grpcSource.getOrigin());
    }

    @Override
    public String getName() {
        return grpcSource.getName();
    }

    @Override
    public long getId() {
        return grpcSource.getId();
    }

    @Override
    public String getOriginNamePair() {
        return getOrigin() + (getName() == null ? "" : getName());
    }

    @Override
    public Eureka2.GrpcSource getGrpcObject() {
        return grpcSource;
    }

    @Override
    public int hashCode() {
        return grpcSource.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof GrpcSourceWrapper) {
            return grpcSource.equals(((GrpcSourceWrapper) obj).getGrpcObject());
        }
        return false;
    }

    @Override
    public String toString() {
        return TextPrinter.toString(grpcSource);
    }

    public static GrpcSourceWrapper asSource(Eureka2.GrpcSource grpcSource) {
        return new GrpcSourceWrapper(grpcSource);
    }

    public static GrpcSourceWrapper newSource(Origin origin, String name, int id) {
        Eureka2.GrpcSource.Builder builder = Eureka2.GrpcSource.newBuilder();
        if (origin != null) {
            builder.setOrigin(toGrpcOrigin(origin));
        }
        if (name != null) {
            builder.setName(name);
        }
        builder.setId(id);
        return new GrpcSourceWrapper(builder.build());
    }

    private static Eureka2.GrpcSource.GrpcOrigin toGrpcOrigin(Origin origin) {
        if (origin == null) {
            return null;
        }
        switch (origin) {
            case BOOTSTRAP:
                return Eureka2.GrpcSource.GrpcOrigin.BOOTSTRAP;
            case INTERESTED:
                return Eureka2.GrpcSource.GrpcOrigin.INTERESTED;
            case LOCAL:
                return Eureka2.GrpcSource.GrpcOrigin.LOCAL;
            case REPLICATED:
                return Eureka2.GrpcSource.GrpcOrigin.REPLICATED;
        }
        throw new IllegalArgumentException("Unrecognized Origin type " + origin);
    }

    private static Origin toOrigin(Eureka2.GrpcSource.GrpcOrigin grpcOrigin) {
        if (grpcOrigin == null) {
            return null;
        }
        switch (grpcOrigin) {
            case BOOTSTRAP:
                return Origin.BOOTSTRAP;
            case REPLICATED:
                return Origin.REPLICATED;
            case INTERESTED:
                return Origin.INTERESTED;
            case LOCAL:
                return Origin.LOCAL;
        }
        throw new IllegalArgumentException("Unrecognized Origin type " + grpcOrigin);
    }
}

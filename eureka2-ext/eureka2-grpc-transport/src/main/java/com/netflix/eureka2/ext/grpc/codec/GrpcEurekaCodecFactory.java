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

package com.netflix.eureka2.ext.grpc.codec;

import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;

import static com.netflix.eureka2.ext.grpc.codec.GrpcEurekaCodec.SUPPORTED_TYPES;

/**
 */
public class GrpcEurekaCodecFactory extends EurekaCodecFactory {

    private final GrpcEurekaCodec codec;

    public GrpcEurekaCodecFactory() {
        this.codec = new GrpcEurekaCodec(SUPPORTED_TYPES);
    }

    @Override
    public <T> boolean accept(Class<T> type) {
        return SUPPORTED_TYPES.contains(type);
    }

    @Override
    public EurekaCodec getCodec() {
        return codec;
    }
}

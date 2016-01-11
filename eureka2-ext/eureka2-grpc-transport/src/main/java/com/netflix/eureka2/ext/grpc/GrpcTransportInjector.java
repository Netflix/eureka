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

package com.netflix.eureka2.ext.grpc;

import com.netflix.eureka2.ext.grpc.codec.GrpcEurekaCodecFactory;
import com.netflix.eureka2.ext.grpc.model.GrpcChannelModel;
import com.netflix.eureka2.ext.grpc.model.GrpcInstanceModel;
import com.netflix.eureka2.ext.grpc.model.GrpcInterestModel;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.spi.model.ChannelModel;

/**
 */
public final class GrpcTransportInjector {

    private GrpcTransportInjector() {
    }

    public static void inject() {
        InterestModel.setDefaultModel(GrpcInterestModel.getGrpcModel());
        InstanceModel.setDefaultModel(GrpcInstanceModel.getGrpcModel());
        ChannelModel.setDefaultModel(GrpcChannelModel.getGrpcModel());

        EurekaCodecFactory.setDefaultFactory(new GrpcEurekaCodecFactory());
    }
}

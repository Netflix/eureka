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

package com.netflix.eureka2;

import com.netflix.eureka2.codec.jackson.JacksonEurekaCodecFactory;
import com.netflix.eureka2.model.*;
import com.netflix.eureka2.model.StdTransportModel;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.spi.model.ChannelModel;
import com.netflix.eureka2.spi.model.TransportModel;

/**
 * Inject standard models in test.
 */
public final class StdTransportInjector {

    private StdTransportInjector() {
    }

    public static void inject() {
        InterestModel.setDefaultModel(StdInterestModel.getStdModel());
        InstanceModel.setDefaultModel(StdInstanceModel.getStdModel());
        TransportModel.setDefaultModel(StdTransportModel.getStdModel());
        ChannelModel.setDefaultModel(StdChannelModel.getStdModel());

        EurekaCodecFactory.setDefaultFactory(new JacksonEurekaCodecFactory());
    }
}

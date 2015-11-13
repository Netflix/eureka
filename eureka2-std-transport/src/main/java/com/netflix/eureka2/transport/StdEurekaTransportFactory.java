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

package com.netflix.eureka2.transport;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.codec.jackson.JacksonEurekaCodecFactory;
import com.netflix.eureka2.protocol.StdAcknowledgement;
import com.netflix.eureka2.protocol.common.StdAddInstance;
import com.netflix.eureka2.protocol.common.StdDeleteInstance;
import com.netflix.eureka2.protocol.common.StdHeartbeat;
import com.netflix.eureka2.protocol.common.StdStreamStateUpdate;
import com.netflix.eureka2.protocol.interest.StdInterestRegistration;
import com.netflix.eureka2.protocol.interest.StdUnregisterInterestSet;
import com.netflix.eureka2.protocol.interest.StdUpdateInstanceInfo;
import com.netflix.eureka2.protocol.register.StdRegister;
import com.netflix.eureka2.protocol.register.StdUnregister;
import com.netflix.eureka2.protocol.replication.StdReplicationHello;
import com.netflix.eureka2.protocol.replication.StdReplicationHelloReply;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.spi.transport.EurekaTransportFactory;
import com.netflix.eureka2.transport.codec.EurekaCodecWrapperFactory;
import io.reactivex.netty.pipeline.PipelineConfigurator;

/**
 */
public class StdEurekaTransportFactory implements EurekaTransportFactory {

    /*
     * Registration protocol constants.
     */
    static final Class<?>[] REGISTRATION_PROTOCOL_MODEL = {
            StdRegister.class, StdUnregister.class, StdHeartbeat.class, StdAcknowledgement.class
    };
    public static final Set<Class<?>> REGISTRATION_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(REGISTRATION_PROTOCOL_MODEL));

    /*
     * Replication protocol constants.
     */
    static final Class<?>[] REPLICATION_PROTOCOL_MODEL = {
            StdReplicationHello.class, StdHeartbeat.class, StdReplicationHelloReply.class,
            StdAddInstance.class, StdDeleteInstance.class, StdStreamStateUpdate.class,
            StdAcknowledgement.class
    };
    static final Set<Class<?>> REPLICATION_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(REPLICATION_PROTOCOL_MODEL));

    /*
     * Discovery protocol constants.
     */
    static final Class<?>[] INTEREST_PROTOCOL_MODEL = {
            StdInterestRegistration.class, StdUnregisterInterestSet.class, StdHeartbeat.class,
            StdAddInstance.class, StdDeleteInstance.class, StdUpdateInstanceInfo.class, StdStreamStateUpdate.class,
            StdAcknowledgement.class
    };
    static final Set<Class<?>> INTEREST_PROTOCOL_MODEL_SET = new HashSet<>(Arrays.asList(INTEREST_PROTOCOL_MODEL));

    /*
     * func methods for creating protocol specific codecs
     */

    static final EurekaCodecFactory REGISTRATION_CODEC_FACTORY = new JacksonEurekaCodecFactory(REGISTRATION_PROTOCOL_MODEL_SET);
    static final EurekaCodecFactory REPLICATION_CODEC_FACTORY = new JacksonEurekaCodecFactory(REPLICATION_PROTOCOL_MODEL_SET);
    static final EurekaCodecFactory INTEREST_CODEC_FACTORY = new JacksonEurekaCodecFactory(INTEREST_PROTOCOL_MODEL_SET);

    @Override
    public PipelineConfigurator<Object, Object> registrationPipeline() {
        return new EurekaPipelineConfigurator(new EurekaCodecWrapperFactory(REGISTRATION_CODEC_FACTORY));
    }

    @Override
    public PipelineConfigurator<Object, Object> replicationPipeline() {
        return new EurekaPipelineConfigurator(new EurekaCodecWrapperFactory(REPLICATION_CODEC_FACTORY));
    }

    @Override
    public PipelineConfigurator<Object, Object> interestPipeline() {
        return new EurekaPipelineConfigurator(new EurekaCodecWrapperFactory(INTEREST_CODEC_FACTORY));
    }
}

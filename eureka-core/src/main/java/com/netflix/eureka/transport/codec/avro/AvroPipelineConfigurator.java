/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.transport.codec.avro;

import java.util.Set;

import com.netflix.eureka.transport.utils.AvroUtils;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.apache.avro.Schema;

/**
 * Pipeline configuration for Avro codec. Avro schema is loaded from
 * a file that is expected to be found on classpath.
 *
 * @author Tomasz Bak
 */
public class AvroPipelineConfigurator implements PipelineConfigurator<Object, Object> {

    private static final int MAX_FRAME_LENGTH = 65536;

    private final Set<Class<?>> protocolTypes;
    private final Schema rootSchema;

    public AvroPipelineConfigurator(Set<Class<?>> protocolTypes, Schema rootSchema) {
        this.protocolTypes = protocolTypes;
        this.rootSchema = rootSchema;
    }

    public AvroPipelineConfigurator(Set<Class<?>> protocolTypes, String schemaResource, String envelopeType) {
        this.protocolTypes = protocolTypes;
        this.rootSchema = AvroUtils.loadSchema(schemaResource, envelopeType);
    }

    @Override
    public void configureNewPipeline(ChannelPipeline pipeline) {
        pipeline.addFirst(new LoggingHandler(LogLevel.ERROR));
        pipeline.addLast(new LengthFieldBasedFrameDecoder(MAX_FRAME_LENGTH, 0, 4, 0, 4));
        pipeline.addLast(new LengthFieldPrepender(4));
        pipeline.addLast(new AvroCodec(protocolTypes, rootSchema));
    }
}

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

import com.netflix.eureka.transport.utils.TransportModel;
import io.netty.channel.ChannelPipeline;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.apache.avro.Schema;

/**
 * @author Tomasz Bak
 */
public class AvroPipelineConfigurator<I, O> implements PipelineConfigurator<I, O> {

    private final Schema schema;
    private final TransportModel model;

    public AvroPipelineConfigurator(TransportModel model) {
        this.model = model;
        schema = MessageBrokerSchema.brokerSchemaFrom(model);
    }

    @Override
    public void configureNewPipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new AvroCodec(schema, model));
    }
}

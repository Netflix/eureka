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

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.utils.TransportModel;
import org.apache.avro.Schema;

/**
 * Avro schema is generated from Java class. Since {@link UserContent} and {@link UserContentWithAck}
 * contain an arbitrary body, we need to construct schema for them explicitly with proper union
 * type set.
 *
 * Alternatively we could expect a client to define schema in Avro text file, and pass it as a
 * parameter when constructing {@link com.netflix.eureka.transport.base.BaseMessageBroker}. This would take however more effort, and
 * is more error prone.
 *
 * @author Tomasz Bak
 */
class MessageBrokerSchema {

    static Schema brokerSchemaFrom(TransportModel model) {
        ConfigurableReflectData reflectData = new ConfigurableReflectData(model);

        List<Schema> schemas = new ArrayList<Schema>();
        schemas.add(reflectData.getSchema(Acknowledgement.class));

        for (Class<?> c : model.getProtocolTypes()) {
            schemas.add(reflectData.getSchema(c));
        }
        return Schema.createUnion(schemas);
    }
}

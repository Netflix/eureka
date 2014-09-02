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

package com.netflix.eureka.transport.utils;

import java.io.IOException;
import java.io.InputStream;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;

/**
 * @author Tomasz Bak
 */
public final class AvroUtils {

    private AvroUtils() {
    }

    public static Schema loadSchema(String schemaResource, String envelopeType) {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(schemaResource);
        if (stream == null) {
            throw new IllegalArgumentException("missing Avro schema document on classpath: " + schemaResource);
        }
        Protocol protocol;
        try {
            protocol = Protocol.parse(stream);
        } catch (IOException e) {
            throw new RuntimeException("IO error during loading Avro schema from file " + schemaResource, e);
        }
        return protocol.getType(envelopeType).getFields().get(0).schema();
    }
}

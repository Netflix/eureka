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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.Schema.Field;
import org.apache.avro.reflect.ReflectData;

/**
 * {@link ReflectData} uses reflection to introspect object and generate
 * schema on-fly. We have our schema defined explicitly with direct mapping
 * to underlying Java data model. We can just reuse it, instead of generation
 * that has multiple shortcomings.
 *
 * @author Tomasz Bak
 */
public class SchemaReflectData extends ReflectData {

    private final Map<String, Schema> schemaMap = new HashMap<>();

    public SchemaReflectData(Schema rootSchema) {
        addSchema(rootSchema);
    }

    private void addSchema(Schema schema) {
        if (schemaMap.containsKey(schema.getFullName())) {
            return;
        }

        switch (schema.getType()) {
            case RECORD:
                schemaMap.put(schema.getFullName(), schema);
                for (Field field : schema.getFields()) {
                    addSchema(field.schema());
                }
                break;
            case ENUM:
                schemaMap.put(schema.getFullName(), schema);
                break;
            case ARRAY:
                addSchema(schema.getElementType());
                break;
            case MAP:
                throw new RuntimeException("MAP type not supported yet");
            case UNION:
                for (Schema unionItem : schema.getTypes()) {
                    addSchema(unionItem);
                }
                break;
            case FIXED:
            case STRING:
            case BYTES:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                // IGNORE
        }
    }

    @Override
    protected Schema createSchema(Type type, Map<String, Schema> names) {
        String name;
        if (type instanceof Class) {
            name = ((Class) type).getName();
        } else if (type instanceof ParameterizedType) {
            name = ((Class) ((ParameterizedType) type).getRawType()).getName();
        } else {
            throw new RuntimeException("unrecognized type class" + type);
        }
        Schema typeSchema = schemaMap.get(name);
        if (typeSchema == null) {
            return super.createSchema(type, names);
        }
        return typeSchema;
    }
}

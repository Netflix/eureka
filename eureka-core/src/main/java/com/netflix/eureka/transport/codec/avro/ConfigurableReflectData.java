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
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.netflix.eureka.transport.utils.TransportModel;
import org.apache.avro.Schema;
import org.apache.avro.reflect.ReflectData;

/**
 * @author Tomasz Bak
 */
public class ConfigurableReflectData extends ReflectData {

    private final TransportModel model;

    public ConfigurableReflectData(TransportModel model) {
        this.model = model;
    }

    @Override
    protected Schema createSchema(Type type, Map<String, Schema> names) {
        Schema schema;
        if (type instanceof TypeVariable) {
            Collection<Type> concreteTypes = model.getFieldTypes(type);
            if (concreteTypes != null) {
                List<Schema> schemas = new ArrayList<Schema>(concreteTypes.size());
                for (Type t : concreteTypes) {
                    schemas.add(getSchema(t));
                }
                schema = Schema.createUnion(schemas);
            } else {
                schema = super.createSchema(type, names);
            }
        } else {
            List<Class<?>> derivedClasses = findHierarchy(type);
            if (derivedClasses != null) {
                List<Schema> schemas = new ArrayList<Schema>(derivedClasses.size());
                for (Class<?> c : derivedClasses) {
                    schemas.add(getSchema(c));
                }
                schema = Schema.createUnion(schemas);
            } else {
                schema = super.createSchema(type, names);
            }
        }
        return schema;
    }

    private List<Class<?>> findHierarchy(Type type) {
        Class<?> rawClass = null;
        if (type instanceof Class) {
            rawClass = (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            rawClass = (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return rawClass == null ? null : model.getDerivedClasses(rawClass);
    }
}

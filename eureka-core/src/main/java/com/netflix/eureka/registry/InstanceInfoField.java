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

package com.netflix.eureka.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author David Liu
 */
public class InstanceInfoField<T> {
    private static final Logger logger = LoggerFactory.getLogger(InstanceInfoField.class);

    public static final InstanceInfoField<String> ID
            = new InstanceInfoField<String>("id", false);

    public static final InstanceInfoField<Long> VERSION
            = new InstanceInfoField<Long>("version", false);

    public static final InstanceInfoField<String> APPLICATION_GROUP
            = new InstanceInfoField<String>("appGroup");

    public static final InstanceInfoField<String> APPLICATION
            = new InstanceInfoField<String>("app");

    public static final InstanceInfoField<String> ASG
            = new InstanceInfoField<String>("asg");

    public static final InstanceInfoField<String> VIP_ADDRESS
            = new InstanceInfoField<String>("vipAddress");

    public static final InstanceInfoField<String> SECURE_VIP_ADDRESS
            = new InstanceInfoField<String>("secureVipAddress");

    public static final InstanceInfoField<String> HOSTNAME
            = new InstanceInfoField<String>("hostname");

    public static final InstanceInfoField<String> IP
            = new InstanceInfoField<String>("ip");

    public static final InstanceInfoField<HashSet<Integer>> PORTS
            = new InstanceInfoField<HashSet<Integer>>("ports");

    public static final InstanceInfoField<HashSet<Integer>> SECURE_PORTS
            = new InstanceInfoField<HashSet<Integer>>("securePorts");

    public static final InstanceInfoField<InstanceInfo.Status> STATUS
            = new InstanceInfoField<InstanceInfo.Status>("status");

    public static final InstanceInfoField<String> HOMEPAGE_URL
            = new InstanceInfoField<String>("homePageUrl");

    public static final InstanceInfoField<String> STATUS_PAGE_URL
            = new InstanceInfoField<String>("statusPageUrl");

    public static final InstanceInfoField<HashSet<String>> HEALTHCHECK_URLS
            = new InstanceInfoField<HashSet<String>>("healthCheckUrls");

    public static final InstanceInfoField<InstanceLocation> INSTANCE_LOCATION
            = new InstanceInfoField<InstanceLocation>("instanceLocation");

    static final Map<String, InstanceInfoField> FIELD_MAP;
    static {
        FIELD_MAP = new HashMap<String, InstanceInfoField>();

        Field[] instanceInfoFields = InstanceInfoField.class.getFields();  // get only public ones
        for (Field field : instanceInfoFields) {
            try {
                InstanceInfoField iif = (InstanceInfoField) field.get(null);
                FIELD_MAP.put(iif.fieldName, iif);
            } catch (IllegalAccessException e) {
                logger.error("Error creating InstanceInfoFields map", e);
            }
        }
    }

    private final String fieldName;
    private final Field field;
    private final boolean updateable;  // denote whether a field is updateable or not (id, version)

    private InstanceInfoField(String fieldName) {
        this(fieldName, true);
    }

    private InstanceInfoField(String fieldName, boolean updateable) {
        this.fieldName = fieldName;
        this.updateable = updateable;

        Field tempField;
        try {
            tempField = InstanceInfo.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            logger.error("The specified field does not exist: " + fieldName, e);
            tempField = null;
        }
        this.field = tempField;
    }

    String getFieldName() {
        return fieldName;
    }

    Type getType() {
        return field.getGenericType();
    }

    Field getField() {
        return field;
    }

    boolean isUpdateable() {
        return updateable;
    }

    Object getValue(InstanceInfo instanceInfo) throws Exception {
        return field.get(instanceInfo);
    }

    private void set(InstanceInfo instanceInfo, T value) throws IllegalArgumentException, IllegalAccessException {
        if (field != null && updateable) {
            field.set(instanceInfo, value);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> void applyTo(InstanceInfo instanceInfo, String fieldName, T value) throws IllegalAccessException {
        if (!FIELD_MAP.containsKey(fieldName)) {
            throw new RuntimeException("This field name does not exist: " + fieldName);
        }
        InstanceInfoField<T> field = FIELD_MAP.get(fieldName);
        field.set(instanceInfo, value);
    }
}

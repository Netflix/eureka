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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author David Liu
 */
public class InstanceInfoField<T> {
    private static final Logger logger = LoggerFactory.getLogger(InstanceInfoField.class);

    // ==================================================================
    public static final InstanceInfoField<String> APPLICATION_GROUP
            = new InstanceInfoField<String>("appGroup", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withAppGroup(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getAppGroup();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> APPLICATION
            = new InstanceInfoField<String>("app", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withApp(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getApp();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> ASG
            = new InstanceInfoField<String>("asg", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withAsg(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getAsg();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> VIP_ADDRESS
            = new InstanceInfoField<String>("vipAddress", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withVipAddress(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getVipAddress();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> SECURE_VIP_ADDRESS
            = new InstanceInfoField<String>("secureVipAddress", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withSecureVipAddress(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getSecureVipAddress();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> HOSTNAME
            = new InstanceInfoField<String>("hostname", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withHostname(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getHostname();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> IP
            = new InstanceInfoField<String>("ip", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withIp(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getIp();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<HashSet<Integer>> PORTS
            = new InstanceInfoField<HashSet<Integer>>("ports", new Accessor<HashSet<Integer>>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, HashSet<Integer> value) {
            return builder.withPorts(value);
        }

        @Override
        public HashSet<Integer> getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getPorts();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<HashSet<Integer>> SECURE_PORTS
            = new InstanceInfoField<HashSet<Integer>>("securePorts", new Accessor<HashSet<Integer>>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, HashSet<Integer> value) {
            return builder.withSecurePorts(value);
        }

        @Override
        public HashSet<Integer> getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getSecurePorts();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<InstanceInfo.Status> STATUS
            = new InstanceInfoField<InstanceInfo.Status>("status", new Accessor<InstanceInfo.Status>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, InstanceInfo.Status value) {
            return builder.withStatus(value);
        }

        @Override
        public InstanceInfo.Status getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getStatus();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> HOMEPAGE_URL
            = new InstanceInfoField<String>("homePageUrl", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withHomePageUrl(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getHomePageUrl();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<String> STATUS_PAGE_URL
            = new InstanceInfoField<String>("statusPageUrl", new Accessor<String>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, String value) {
            return builder.withStatusPageUrl(value);
        }

        @Override
        public String getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getStatusPageUrl();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<HashSet<String>> HEALTHCHECK_URLS
            = new InstanceInfoField<HashSet<String>>("healthCheckUrls", new Accessor<HashSet<String>>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, HashSet<String> value) {
            return builder.withHealthCheckUrls(value);
        }

        @Override
        public HashSet<String> getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getHealthCheckUrls();
        }
    });

    // ==================================================================
    public static final InstanceInfoField<InstanceLocation> INSTANCE_LOCATION
            = new InstanceInfoField<InstanceLocation>("instanceLocation", new Accessor<InstanceLocation>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, InstanceLocation value) {
            return builder.withInstanceLocation(value);
        }

        @Override
        public InstanceLocation getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getInstanceLocation();
        }
    });


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
    private final Accessor<T> accessor;
    private final Type valueType;

    private InstanceInfoField(String fieldName, Accessor<T> accessor) {
        this.fieldName = fieldName;
        this.accessor = accessor;

        //TODO: remove once/if we no longer need avro
        valueType = ((ParameterizedType) accessor.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
    }

    InstanceInfo.Builder update(InstanceInfo.Builder builder, T value) {
        accessor.update(builder, value);
        return builder;
    }

    String getFieldName() {
        return fieldName;
    }

    Type getValueType() {
        return valueType;
    }

    T getValue(InstanceInfo instanceInfo) throws Exception {
        return accessor.getValue(instanceInfo);
    }

    @SuppressWarnings("unchecked")
    static <T> InstanceInfo.Builder applyTo(InstanceInfo.Builder instanceInfoBuilder, String fieldName, T value) throws Exception {
        if (!FIELD_MAP.containsKey(fieldName)) {
            throw new RuntimeException("This field name does not exist: " + fieldName);
        }
        InstanceInfoField<T> field = FIELD_MAP.get(fieldName);
        return field.update(instanceInfoBuilder, value);
    }

    private interface Accessor<T> {
        InstanceInfo.Builder update(InstanceInfo.Builder builder, T value);

        T getValue(InstanceInfo instanceInfo);
    }

}

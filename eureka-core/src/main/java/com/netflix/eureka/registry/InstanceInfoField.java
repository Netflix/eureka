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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author David Liu
 */
public class InstanceInfoField<T> {

    public enum Name {

        AppGroup,
        App,
        Asg,
        VipAddress,
        SecureVipAddress,
        Hostname,
        Ip,
        Ports,
        SecurePorts,
        Status,
        HomePageUrl,
        StatusPageUrl,
        HealthCheckUrls,
        DataCenterInfo;

        // Since enum values are capitalized, and field names start with lowercase letter,
        // for case of comparison we comper lowercased names.
        private static final Map<String, Name> nameStrVsName = new HashMap<String, Name>();

        static {
            updateNames();
        }

        private static void updateNames() {
            for (Name name : values()) {
                nameStrVsName.put(name.name(), name);
            }
        }

        public static Name forName(String name) {
            return nameStrVsName.get(name);
        }
    }

    // ==================================================================
    public static final InstanceInfoField<String> APPLICATION_GROUP
            = new InstanceInfoField<String>(Name.AppGroup, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.App, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.Asg, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.VipAddress, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.SecureVipAddress, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.Hostname, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.Ip, new Accessor<String>() {
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
            = new InstanceInfoField<HashSet<Integer>>(Name.Ports, new Accessor<HashSet<Integer>>() {
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
            = new InstanceInfoField<HashSet<Integer>>(Name.SecurePorts, new Accessor<HashSet<Integer>>() {
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
            = new InstanceInfoField<InstanceInfo.Status>(Name.Status, new Accessor<InstanceInfo.Status>() {
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
            = new InstanceInfoField<String>(Name.HomePageUrl, new Accessor<String>() {
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
            = new InstanceInfoField<String>(Name.StatusPageUrl, new Accessor<String>() {
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
            = new InstanceInfoField<HashSet<String>>(Name.HealthCheckUrls, new Accessor<HashSet<String>>() {
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
    public static final InstanceInfoField<DataCenterInfo> DATA_CENTER_INFO
            = new InstanceInfoField<DataCenterInfo>(Name.DataCenterInfo, new Accessor<DataCenterInfo>() {
        @Override
        public InstanceInfo.Builder update(InstanceInfo.Builder builder, DataCenterInfo value) {
            return builder.withInstanceLocation(value);
        }

        @Override
        public DataCenterInfo getValue(InstanceInfo instanceInfo) {
            return instanceInfo.getDataCenterInfo();
        }
    });

    private final Name fieldName;
    private final Accessor<T> accessor;
    private final Type valueType;

    private InstanceInfoField(Name fieldName, Accessor<T> accessor) {
        this.fieldName = fieldName;
        this.accessor = accessor;

        //TODO: remove once/if we no longer need avro
        valueType = ((ParameterizedType) accessor.getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
    }

    InstanceInfo.Builder update(InstanceInfo.Builder builder, T value) {
        accessor.update(builder, value);
        return builder;
    }

    public Name getFieldName() {
        return fieldName;
    }

    public Type getValueType() {
        return valueType;
    }

    public T getValue(InstanceInfo instanceInfo) {
        return accessor.getValue(instanceInfo);
    }

    @SuppressWarnings("unchecked")
    public static <T> InstanceInfoField<T> forName(Name name) {
        switch (name) {
            case AppGroup:
                return (InstanceInfoField<T>) APPLICATION_GROUP;
            case App:
                return (InstanceInfoField<T>) APPLICATION;
            case Asg:
                return (InstanceInfoField<T>) ASG;
            case VipAddress:
                return (InstanceInfoField<T>) VIP_ADDRESS;
            case SecureVipAddress:
                return (InstanceInfoField<T>) SECURE_VIP_ADDRESS;
            case Hostname:
                return (InstanceInfoField<T>) HOSTNAME;
            case Ip:
                return (InstanceInfoField<T>) IP;
            case Ports:
                return (InstanceInfoField<T>) PORTS;
            case SecurePorts:
                return (InstanceInfoField<T>) SECURE_PORTS;
            case Status:
                return (InstanceInfoField<T>) STATUS;
            case HomePageUrl:
                return (InstanceInfoField<T>) HOMEPAGE_URL;
            case StatusPageUrl:
                return (InstanceInfoField<T>) STATUS_PAGE_URL;
            case HealthCheckUrls:
                return (InstanceInfoField<T>) HEALTHCHECK_URLS;
            case DataCenterInfo:
                return (InstanceInfoField<T>) DATA_CENTER_INFO;
        }
        throw new IllegalArgumentException("Unhandled name: " + name);
    }

    private interface Accessor<T> {
        InstanceInfo.Builder update(InstanceInfo.Builder builder, T value);

        T getValue(InstanceInfo instanceInfo);
    }

}

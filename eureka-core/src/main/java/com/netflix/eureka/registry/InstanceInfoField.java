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

import java.lang.reflect.Field;
import java.util.Set;

/**
 * @author David Liu
 */
public class InstanceInfoField<T> {
    public static final InstanceInfoField<String> ID
            = new InstanceInfoField<String>("id");

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

    public static final InstanceInfoField<Set<Integer>> PORTS
            = new InstanceInfoField<Set<Integer>>("ports");

    public static final InstanceInfoField<Set<Integer>> SECURE_PORTS
            = new InstanceInfoField<Set<Integer>>("securePorts");

    public static final InstanceInfoField<InstanceInfo.Status> STATUS
            = new InstanceInfoField<InstanceInfo.Status>("status");

    public static final InstanceInfoField<String> HOMEPAGE_URL
            = new InstanceInfoField<String>("homePageUrl");

    public static final InstanceInfoField<String> STATUS_PAGE_URL
            = new InstanceInfoField<String>("statusPageUrl");

    public static final InstanceInfoField<Set<String>> HEALTHCHECK_URLS
            = new InstanceInfoField<Set<String>>("healthCheckUrls");

    public static final InstanceInfoField<String> VERSION
            = new InstanceInfoField<String>("version");

    public static final InstanceInfoField<InstanceLocation> INSTANCE_LOCATION
            = new InstanceInfoField<InstanceLocation>("instanceLocation");


    private final Field field;

    private InstanceInfoField(String fieldName) {
        Field tempField;
        try {
            tempField = InstanceInfo.class.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            // log.error
            tempField = null;
        }
        this.field = tempField;
    }

    public Field getField() {
        return field;
    }

    public void set(InstanceInfo instanceInfo, T value) throws IllegalArgumentException, IllegalAccessException {
        if (field != null) {
            field.set(instanceInfo, value);
        }
    }
}

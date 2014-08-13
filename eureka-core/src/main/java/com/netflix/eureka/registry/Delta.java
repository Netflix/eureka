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

/**
 * A matching pair of field:value that denotes a delta change to an InstanceInfo
 * @param <T> the type of the delta value
 *
 * @author David Liu
 */
public class Delta<T> {
    private final InstanceInfoField<T> field;
    private final T value;

    public Delta(InstanceInfoField<T> field, T value) {
        this.field = field;
        this.value = value;
    }

    public void applyTo(InstanceInfo instanceInfo) throws IllegalArgumentException, IllegalAccessException {
        if (field != null) {
            field.set(instanceInfo, value);
        }
    }
}

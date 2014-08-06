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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Tomasz Bak
 */
public class TransportModel {

    private final Set<Class<?>> protocolTypes;
    private final Map<Class<?>, List<Class<?>>> classHierarchies;

    public TransportModel(Class<?>[] protocolTypes, Map<Class<?>, List<Class<?>>> classHierarchies) {
        this.protocolTypes = new HashSet<Class<?>>(protocolTypes.length);
        Collections.addAll(this.protocolTypes, protocolTypes);
        this.classHierarchies = classHierarchies;
    }

    public boolean isProtocolMessage(Object msg) {
        return protocolTypes.contains(msg.getClass());
    }

    public Set<Class<?>> getProtocolTypes() {
        return protocolTypes;
    }

    public <T> boolean isKnownAbstract(Class<T> type) {
        return classHierarchies.containsKey(type);
    }

    public List<Class<?>> getClassHierarchies(Class<?> type) {
        return classHierarchies.get(type);
    }

    public static class TransportModelBuilder {

        private final Class<?>[] messageTypes;

        private final Map<Class<?>, List<Class<?>>> classHierarchies = new HashMap<Class<?>, List<Class<?>>>();

        public TransportModelBuilder(Class<?>... messageTypes) {
            this.messageTypes = messageTypes;
        }

        public <T> TransportModelBuilder withHierarchy(Class<T> from, Class<? extends T>... derivedClasses) {
            classHierarchies.put(from, Arrays.asList((Class<?>[]) derivedClasses));
            return this;
        }

        public TransportModel build() {
            return new TransportModel(messageTypes, classHierarchies);
        }
    }
}

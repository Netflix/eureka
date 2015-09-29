/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.discovery.util;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;

/**
 * @author Tomasz Bak
 */
public class EurekaEntityTransformers {

    /**
     * Interface for Eureka entity transforming operators.
     */
    public interface Transformer<T> {

        T apply(T value);
    }

    public static <T> Transformer<T> identity() {
        return (Transformer<T>) IDENTITY_TRANSFORMER;
    }

    public static Transformer<InstanceInfo> actionTypeSetter(ActionType actionType) {
        switch (actionType) {
            case ADDED:
                return ADD_ACTION_SETTER_TRANSFORMER;
            case MODIFIED:
                return MODIFIED_ACTION_SETTER_TRANSFORMER;
            case DELETED:
                return DELETED_ACTION_SETTER_TRANSFORMER;
        }
        throw new IllegalStateException("Unhandled ActionType value " + actionType);
    }

    private static final Transformer<Object> IDENTITY_TRANSFORMER = new Transformer<Object>() {
        @Override
        public Object apply(Object value) {
            return value;
        }
    };

    private static final Transformer<InstanceInfo> ADD_ACTION_SETTER_TRANSFORMER = new Transformer<InstanceInfo>() {
        @Override
        public InstanceInfo apply(InstanceInfo instance) {
            InstanceInfo copy = new InstanceInfo(instance);
            copy.setActionType(ActionType.ADDED);
            return copy;
        }
    };

    private static final Transformer<InstanceInfo> MODIFIED_ACTION_SETTER_TRANSFORMER = new Transformer<InstanceInfo>() {
        @Override
        public InstanceInfo apply(InstanceInfo instance) {
            InstanceInfo copy = new InstanceInfo(instance);
            copy.setActionType(ActionType.MODIFIED);
            return copy;
        }
    };

    private static final Transformer<InstanceInfo> DELETED_ACTION_SETTER_TRANSFORMER = new Transformer<InstanceInfo>() {
        @Override
        public InstanceInfo apply(InstanceInfo instance) {
            InstanceInfo copy = new InstanceInfo(instance);
            copy.setActionType(ActionType.DELETED);
            return copy;
        }
    };
}

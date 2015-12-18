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

package com.netflix.eureka2.internal.util;

import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoField;

/**
 */
public final class InstanceUtil {

    private InstanceUtil() {
    }

    public static String toStringSummary(InstanceInfo instance) {
        boolean hasMeta = instance.getMetaData() != null && !instance.getMetaData().isEmpty();
        return "InstanceInfo{" +
                "id='" + instance.getId() + '\'' +
                ", app='" + instance.getApp() + '\'' +
                ", asg='" + instance.getAsg() + '\'' +
                ", status=" + instance.getStatus() +
                ", metaData={" + (hasMeta ? instance.getMetaData().size() + " items}" : "no items}") +
                ", dataCenterInfo=" + (instance.getDataCenterInfo() == null ? "null" : "{name=" + instance.getDataCenterInfo().getName() + '}') +
                '}';
    }

    public static Set<Delta<?>> diff(InstanceInfo oldInstanceInfo, InstanceInfo newInstanceInfo) {
        if (oldInstanceInfo == null || newInstanceInfo == null) {
            return null;
        }

        if (!oldInstanceInfo.getId().equals(newInstanceInfo.getId())) {
            return null;
        }

        Set<Delta<?>> deltas = new HashSet<Delta<?>>();

        for (InstanceInfoField.Name fieldName : InstanceInfoField.Name.values()) {
            InstanceInfoField<Object> field = InstanceInfoField.forName(fieldName);
            Object oldValue = field.getValue(oldInstanceInfo);
            Object newValue = field.getValue(newInstanceInfo);

            if (!equalsNullable(oldValue, newValue)) {  // there is a difference
                Delta<?> delta = InstanceModel.getDefaultModel().newDelta()
                        .withId(newInstanceInfo.getId())
                        .withDelta(field, newValue)
                        .build();
                if(deltas.contains(delta)) {
                    System.out.println("Duplicate!");
                }
                deltas.add(delta);
            } else {
                System.out.println("Matching");
            }

        }

        return deltas;
    }

    private static boolean equalsNullable(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        } else if (a == null || b == null) {
            return false;
        } else {
            return a.equals(b);
        }
    }

}

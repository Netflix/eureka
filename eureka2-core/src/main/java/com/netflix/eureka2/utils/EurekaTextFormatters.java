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

package com.netflix.eureka2.utils;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Set;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.MultipleInterests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;

/**
 */
public final class EurekaTextFormatters {

    private EurekaTextFormatters() {
    }

    /**
     * String representation of {@link InstanceInfo} object with a few essential fields.
     */
    public static String toShortString(InstanceInfo instance) {
        StringBuilder sb = new StringBuilder();

        sb.append("InstanceInfo{");
        sb.append("id=").append(instance.getId());
        sb.append(", app=").append(instance.getApp());
        sb.append(", status=").append(instance.getStatus());

        sb.append('}');

        return sb.toString();
    }

    public static String toShortString(Source source) {
        StringBuilder sb = new StringBuilder();
        sb.append(source.getOrigin() == null ? "<no origin>" : source.getOrigin());
        sb.append('/').append(source.getName() == null ? "<null>" : source.getName());
        sb.append('/').append(source.getId());
        return sb.toString();
    }

    public static String toShortString(Set<Delta<?>> deltas) {
        StringBuilder sb = new StringBuilder();

        sb.append('[');
        for (Delta<?> delta : deltas) {
            sb.append('<');
            sb.append(delta.getField().getFieldName());
            sb.append(',');
            sb.append(delta.getValue());
            sb.append('>');
        }
        sb.append(']');
        return sb.toString();
    }

    public static String toShortString(ChangeNotification<InstanceInfo> notification) {
        StringBuilder sb = new StringBuilder();

        sb.append(notification.getClass().getSimpleName());
        sb.append('{');
        if (notification instanceof Sourced) {
            sb.append("source=").append(toShortString(((Sourced) notification).getSource()));
            sb.append(", ");
        }
        sb.append("kind=").append(notification.getKind());
        switch (notification.getKind()) {
            case Add:
                sb.append(", data=").append(toShortString(notification.getData()));
                break;
            case Modify:
                sb.append(", data=").append(toShortString(notification.getData()));
                sb.append(", delta=").append(toShortString(((ModifyNotification) notification).getDelta()));
                break;
            case Delete:
                sb.append(", id=").append(notification.getData().getId());
                break;
            case BufferSentinel:
                if (notification instanceof StreamStateNotification) {
                    sb.append(", marker=").append(((StreamStateNotification) notification).getBufferState());
                }
                break;
        }
        sb.append('}');
        return sb.toString();
    }

    public static String toShortString(Object object) {
        if (object instanceof InstanceInfo) {
            return toShortString((InstanceInfo) object);
        }
        if (object instanceof Source) {
            return toShortString((Source) object);
        }
        if (object instanceof ChangeNotification) {
            return toShortString((ChangeNotification) object);
        }
        if (object instanceof Interest) {
            return toQuery((Interest<InstanceInfo>) object);
        }
        return object.toString();
    }

    /**
     * Format interest query using abstract, SQL like notation.
     */
    public static <T> String toQuery(Interest<T> interest) {
        StringBuilder output = new StringBuilder("Interest{");
        toQuery(output, interest);
        output.append('}');
        return output.toString();
    }

    private static final EnumMap<Interest.QueryType, String> queryKeyName = new EnumMap<Interest.QueryType, String>(Interest.QueryType.class);

    static {
        queryKeyName.put(Interest.QueryType.Application, "application");
        queryKeyName.put(Interest.QueryType.Instance, "instance");
        queryKeyName.put(Interest.QueryType.SecureVip, "secureVip");
        queryKeyName.put(Interest.QueryType.Vip, "vip");
        queryKeyName.put(Interest.QueryType.Any, "any");
        queryKeyName.put(Interest.QueryType.Composite, "composite");
        queryKeyName.put(Interest.QueryType.None, "none");
    }

    private static <T> void toQuery(StringBuilder output, Interest<T> interest) {
        Interest.QueryType query = interest.getQueryType();
        if (query == Interest.QueryType.Any || query == Interest.QueryType.None) {
            output.append(queryKeyName.get(query));
        } else if (query != Interest.QueryType.Composite) {
            output.append(queryKeyName.get(query));
            output.append(interest.getOperator() == Interest.Operator.Equals ? '=' : "~=");
            output.append(interest.getPattern());
        } else {
            MultipleInterests<InstanceInfo> multiple = (MultipleInterests<InstanceInfo>) interest;
            Iterator<Interest<InstanceInfo>> it = multiple.getInterests().iterator();

            if (!it.hasNext()) {
                output.append("none");
                return;
            }

            toQuery(output, it.next());
            while (it.hasNext()) {
                output.append(" & ");
                toQuery(output, it.next());
            }
        }
    }
}

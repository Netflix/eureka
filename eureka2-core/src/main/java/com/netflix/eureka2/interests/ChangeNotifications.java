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

package com.netflix.eureka2.interests;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.Sourced;
import rx.Observable;

/**
 * Collection of transformation functions operating on {@link ChangeNotification} data.
 *
 * @author Tomasz Bak
 */
public final class ChangeNotifications {

    private ChangeNotifications() {
    }

    public static <T> Observable<ChangeNotification<T>> from(T... values) {
        if (values == null || values.length == 0) {
            return Observable.empty();
        }
        List<ChangeNotification<T>> notifications = new ArrayList<>(values.length);
        for (T value : values) {
            notifications.add(new ChangeNotification<T>(Kind.Add, value));
        }
        return Observable.from(notifications);
    }

    /**
     * Make a new copy of {@link ChangeNotification} object with a new state. As this method
     * does not belong to {@link ChangeNotification}'s public API, it is implemented here in
     * the utility class.
     */
    public static <T> ChangeNotification<T> copyWithState(ChangeNotification<T> notification,
                                                          StreamState<T> streamState) {
        if (notification.getClass().isAssignableFrom(ChangeNotification.class)) {
            return new ChangeNotification<>(notification.getKind(), notification.getData(), streamState);
        }
        if (notification.getClass().isAssignableFrom(ModifyNotification.class)) {
            return new ModifyNotification<>(
                    notification.getData(),
                    ((ModifyNotification<ChangeNotification<T>>) notification).getDelta(),
                    streamState
            );
        }
        if (notification.getClass().isAssignableFrom(SourcedChangeNotification.class)) {
            return new SourcedChangeNotification<>(
                    notification.getKind(),
                    notification.getData(),
                    ((Sourced) notification).getSource(),
                    streamState);
        }
        if (notification.getClass().isAssignableFrom(SourcedModifyNotification.class)) {
            return new SourcedModifyNotification<>(
                    notification.getData(),
                    ((ModifyNotification<ChangeNotification<T>>) notification).getDelta(),
                    ((Sourced) notification).getSource(),
                    streamState
            );
        }

        throw new IllegalStateException("Unrecognized ChangeNotification type " + notification.getClass());
    }
}

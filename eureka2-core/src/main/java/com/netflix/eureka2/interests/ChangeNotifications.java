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
import rx.Observable;
import rx.functions.Func1;

/**
 * Collection of transformation functions operating on {@link ChangeNotification} data.
 *
 * @author Tomasz Bak
 */
public final class ChangeNotifications {

    private static final Func1<ChangeNotification<?>, Boolean> DATA_ONLY_FILTER_FUNC =
            new Func1<ChangeNotification<?>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<?> notification) {
                    return notification.isDataNotification();
                }
            };

    private static final Func1<ChangeNotification<?>, Boolean> STREAM_STATE_FILTER_FUNC =
            new Func1<ChangeNotification<?>, Boolean>() {
                @Override
                public Boolean call(ChangeNotification<?> notification) {
                    return notification instanceof StreamStateNotification;
                }
            };

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

    public static Func1<ChangeNotification<?>, Boolean> dataOnlyFilter() {
        return DATA_ONLY_FILTER_FUNC;
    }

    public static Func1<ChangeNotification<?>, Boolean> streamStateFilter() {
        return STREAM_STATE_FILTER_FUNC;
    }
}

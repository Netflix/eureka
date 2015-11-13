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

package com.netflix.eureka2.model.interest;

import java.util.Set;

/**
 * Eureka client can subscribe to multiple interests at the same time. This class
 * describes such multiple interests subscription.
 *
 * Note after a multipleInterests is created, the internal set of interests are always
 * flattened to the base-level interests
 *
 * @author Tomasz Bak
 */
public interface MultipleInterests<T> extends Interest<T> {

    Set<Interest<T>> getInterests();

    Set<Interest<T>> flatten();

    MultipleInterests<T> copyAndAppend(Interest<T> toAppend);

    MultipleInterests<T> copyAndRemove(Interest<T> toAppend);
}

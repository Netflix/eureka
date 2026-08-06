/*
 * Copyright 2026 Netflix, Inc.
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

import java.util.HashMap;

/**
 * Utility methods for working with Maps.
 */
public final class MapUtil {

    private MapUtil() {
        // Utility class
    }

    /**
     * Creates a HashMap with enough capacity to hold the expected number of entries
     * without resizing. This is equivalent to Guava's Maps.newHashMapWithExpectedSize().
     *
     * @param expectedSize the expected number of entries
     * @param <K> the key type
     * @param <V> the value type
     * @return a new HashMap with appropriate initial capacity
     */
    public static <K, V> HashMap<K, V> newHashMapWithExpectedSize(int expectedSize) {
        // HashMap resizes at load factor 0.75, so we need capacity = expectedSize / 0.75 + 1
        return new HashMap<>((int) (expectedSize / 0.75f) + 1);
    }
}

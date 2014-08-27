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

package com.netflix.eureka.interests;

import java.util.Arrays;

/**
 * Eureka client can subscribe to multiple interests at the same time. This class
 * describes such multiple interests subscription.
 *
 * @author Tomasz Bak
 */
public class MultipleInterests<T> extends Interest<T> {

    private final Interest<T>[] interests;

    protected MultipleInterests() {
        this.interests = null;
    }

    public MultipleInterests(Interest<T>... interests) {
        this.interests = interests;
    }

    public Interest<T>[] getInterests() {
        return interests;
    }

    @Override
    public boolean matches(T data) {
        for (Interest<T> interest : interests) {
            if (interest.matches(data)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MultipleInterests that = (MultipleInterests) o;

        if (!Arrays.equals(interests, that.interests)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return interests != null ? Arrays.hashCode(interests) : 0;
    }

    @Override
    public String toString() {
        return "MultipleInterests{interests=" + Arrays.toString(interests) + '}';
    }
}

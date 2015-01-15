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

package com.netflix.eureka2.protocol.discovery;

/**
 * @author Tomasz Bak
 */
public class UnregisterInterestSet {

    public static final UnregisterInterestSet INSTANCE = new UnregisterInterestSet();

    private static final int HASH = 432412432;

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UnregisterInterestSet;
    }
}

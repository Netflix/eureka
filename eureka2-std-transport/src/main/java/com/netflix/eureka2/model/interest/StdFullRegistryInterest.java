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

/**
 * @author Nitesh Kant
 */
public class StdFullRegistryInterest<T> implements Interest<T> {

    private static final StdFullRegistryInterest<?> DEFAULT_INSTANCE = new StdFullRegistryInterest<>();

    private static final int HASH = 234234128;

    public static <T> StdFullRegistryInterest<T> getInstance() {
        return (StdFullRegistryInterest<T>) DEFAULT_INSTANCE;
    }

    @Override
    public QueryType getQueryType() {
        return QueryType.Any;
    }

    @Override
    public Operator getOperator() {
        return null;
    }

    @Override
    public String getPattern() {
        return null;
    }

    @Override
    public boolean matches(T data) {
        return true;
    }

    @Override
    public boolean isAtomicInterest() {
        return true;
    }

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StdFullRegistryInterest;
    }

    @Override
    public String toString() {
        return "StdFullRegistryInterest{}";
    }
}

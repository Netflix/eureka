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

package com.netflix.eureka.registry;

/**
 * Represent a lease over an element E
 * @author David Liu
 */
public class Lease<E> {

    private final E holder;

    public Lease(E holder) {
        this.holder = holder;
    }

    public E getHolder() {
        return holder;
    }

    public void renew(long durationMillis) {

    }

    public boolean hasExpired() {
        return false;
    }

    public void cancel() {

    }
}

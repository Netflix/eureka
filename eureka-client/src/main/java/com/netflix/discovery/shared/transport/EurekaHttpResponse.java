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

package com.netflix.discovery.shared.transport;

import java.net.URI;

/**
 * @author Tomasz Bak
 */
public class EurekaHttpResponse<T> {
    private final int statusCode;
    private final T entity;
    private URI location;

    public EurekaHttpResponse(int statusCode) {
        this(statusCode, null);
    }

    public EurekaHttpResponse(int statusCode, T entity) {
        this.statusCode = statusCode;
        this.entity = entity;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public URI getLocation() {
        throw new IllegalStateException("not implemented yet");
    }

    public T getEntity() {
        return entity;
    }

    public static <T> EurekaHttpResponse<T> responseWith(int status) {
        return new EurekaHttpResponse<>(status, null);
    }

    public static <T> EurekaHttpResponse<T> responseWith(int status, T entity) {
        return new EurekaHttpResponse<>(status, entity);
    }
}

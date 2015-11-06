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

package com.netflix.eureka.resources.filter;

import javax.ws.rs.core.HttpHeaders;

import com.sun.jersey.api.container.filter.GZIPContentEncodingFilter;
import com.sun.jersey.spi.container.ContainerRequest;

/**
 * The Jersey implementation removes ETag, if it was not set by it. As ETag values in response are fully managed by
 * REST API directly, the "If-None-Match" header must be preserved.
 */
public class EurekaGZIPContentEncodingFilter extends GZIPContentEncodingFilter {

    @Override
    public ContainerRequest filter(ContainerRequest request) {
        String entityTag = request.getRequestHeaders().getFirst(HttpHeaders.IF_NONE_MATCH);
        super.filter(request);
        if (entityTag != null && request.getRequestHeaders().getFirst(HttpHeaders.IF_NONE_MATCH) == null) {
            request.getRequestHeaders().putSingle(HttpHeaders.IF_NONE_MATCH, entityTag);
        }
        return request;
    }
}

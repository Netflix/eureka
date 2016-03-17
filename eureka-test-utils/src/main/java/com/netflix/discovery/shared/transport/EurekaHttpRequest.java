/*
 * Copyright 2016 Netflix, Inc.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 */
public class EurekaHttpRequest {

    private final String requestMethod;
    private final URI requestURI;
    private final Map<String, String> headers;

    public EurekaHttpRequest(String requestMethod, URI requestURI, Map<String, String> headers) {
        this.requestMethod = requestMethod;
        this.requestURI = requestURI;
        this.headers = Collections.unmodifiableMap(new HashMap<String, String>(headers));
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public URI getRequestURI() {
        return requestURI;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}

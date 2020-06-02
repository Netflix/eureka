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

package com.netflix.discovery.shared.transport.jersey;

import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource.Builder;

import java.util.Map;

/**
 * A version of Jersey1 {@link EurekaHttpClient} to be used by applications.
 *
 * @author Tomasz Bak
 */
public class JerseyApplicationClient extends AbstractJerseyEurekaHttpClient {

    private final Map<String, String> additionalHeaders;

    public JerseyApplicationClient(Client jerseyClient, String serviceUrl, Map<String, String> additionalHeaders) {
        super(jerseyClient, serviceUrl);
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    protected void addExtraHeaders(Builder webResource) {
        if (additionalHeaders != null) {
            for (String key : additionalHeaders.keySet()) {
                webResource.header(key, additionalHeaders.get(key));
            }
        }
    }
}

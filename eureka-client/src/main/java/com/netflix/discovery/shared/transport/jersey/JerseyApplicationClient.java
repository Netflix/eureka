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

/**
 * A version of Jersey {@link EurekaHttpClient} to be used by applications.
 *
 * @author Tomasz Bak
 */
public class JerseyApplicationClient extends AbstractJerseyEurekaHttpClient {

    public static final String HTTP_X_DISCOVERY_ALLOW_REDIRECT = "X-Discovery-AllowRedirect";

    private final boolean allowRedirect;

    public JerseyApplicationClient(Client jerseyClient, String serviceUrl, boolean allowRedirect) {
        super(jerseyClient, serviceUrl);
        this.allowRedirect = allowRedirect;
    }

    @Override
    protected void addExtraHeaders(Builder webResource) {
        if (allowRedirect) {
            webResource.header(HTTP_X_DISCOVERY_ALLOW_REDIRECT, "true");
        }
    }
}

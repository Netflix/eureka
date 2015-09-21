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

package com.netflix.discovery.shared.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Tomasz Bak
 */
public class EurekaEndpoint {

    private final String hostName;
    private final int port;
    private final boolean isSecure;
    private final String relativeUri;
    private final String zone;
    private final String serviceUrl;

    public EurekaEndpoint(String hostName, int port, boolean isSecure, String relativeUri, String zone) {
        this.hostName = hostName;
        this.port = port;
        this.isSecure = isSecure;
        this.relativeUri = relativeUri;
        this.zone = zone;

        StringBuilder sb = new StringBuilder()
                .append(isSecure ? "https" : "http")
                .append("://")
                .append(hostName)
                .append(':')
                .append(port);
        if (relativeUri != null) {
            if (!relativeUri.startsWith("/")) {
                sb.append('/');
            }
            sb.append(relativeUri);
        }
        this.serviceUrl = sb.toString();
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }

    public boolean isSecure() {
        return isSecure;
    }

    public String getRelativeUri() {
        return relativeUri;
    }

    public String getZone() {
        return zone;
    }

    public static List<EurekaEndpoint> createForServerList(List<String> hostNames, int port, boolean isSecure, String relativeUri, String zone) {
        if (hostNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<EurekaEndpoint> eurekaEndpoints = new ArrayList<>(hostNames.size());
        for (String hostName : hostNames) {
            eurekaEndpoints.add(new EurekaEndpoint(hostName, port, isSecure, relativeUri, zone));
        }
        return eurekaEndpoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        EurekaEndpoint endpoint = (EurekaEndpoint) o;

        if (port != endpoint.port)
            return false;
        if (isSecure != endpoint.isSecure)
            return false;
        if (hostName != null ? !hostName.equals(endpoint.hostName) : endpoint.hostName != null)
            return false;
        if (relativeUri != null ? !relativeUri.equals(endpoint.relativeUri) : endpoint.relativeUri != null)
            return false;
        return !(zone != null ? !zone.equals(endpoint.zone) : endpoint.zone != null);

    }

    @Override
    public int hashCode() {
        int result = hostName != null ? hostName.hashCode() : 0;
        result = 31 * result + port;
        result = 31 * result + (isSecure ? 1 : 0);
        result = 31 * result + (relativeUri != null ? relativeUri.hashCode() : 0);
        result = 31 * result + (zone != null ? zone.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "EurekaEndpoint{ serviceUrl='" + serviceUrl + '\'' + ", zone='" + zone + '\'' + '}';
    }
}

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

package com.netflix.discovery.shared.transport.jersey2;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.EurekaHttpResponse.EurekaHttpResponseBuilder;
import com.netflix.discovery.util.StringUtil;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractJersey2EurekaHttpClient implements EurekaHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(AbstractJersey2EurekaHttpClient.class);

    protected final Client jerseyClient;
    protected final String serviceUrl;
    private final String userName;
    private final String password;

    public AbstractJersey2EurekaHttpClient(Client jerseyClient, String serviceUrl) {
        this.jerseyClient = jerseyClient;
        this.serviceUrl = serviceUrl;

        // Jersey2 does not read credentials from the URI. We extract it here and enable authentication feature.
        String localUserName = null;
        String localPassword = null;
        try {
            URI serviceURI = new URI(serviceUrl);
            if (serviceURI.getUserInfo() != null) {
                String[] credentials = serviceURI.getUserInfo().split(":");
                if (credentials.length == 2) {
                    localUserName = credentials[0];
                    localPassword = credentials[1];
                }
            }
        } catch (URISyntaxException ignore) {
        }
        this.userName = localUserName;
        this.password = localPassword;
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        String urlPath = "apps/" + info.getAppName();
        Response response = null;
        try {
            Builder resourceBuilder = jerseyClient.target(serviceUrl).path(urlPath).request();
            addExtraProperties(resourceBuilder);
            addExtraHeaders(resourceBuilder);
            response = resourceBuilder
                    .accept(MediaType.APPLICATION_JSON)
                    .acceptEncoding("gzip")
                    .post(Entity.json(info));
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP POST {}/{} with instance {}; statusCode={}", serviceUrl, urlPath, info.getId(),
                        response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Void> cancel(String appName, String id) {
        String urlPath = "apps/" + appName + '/' + id;
        Response response = null;
        try {
            Builder resourceBuilder = jerseyClient.target(serviceUrl).path(urlPath).request();
            addExtraProperties(resourceBuilder);
            addExtraHeaders(resourceBuilder);
            response = resourceBuilder.delete();
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP DELETE {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        String urlPath = "apps/" + appName + '/' + id;
        Response response = null;
        try {
            WebTarget webResource = jerseyClient.target(serviceUrl)
                    .path(urlPath)
                    .queryParam("status", info.getStatus().toString())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                webResource = webResource.queryParam("overriddenstatus", overriddenStatus.name());
            }
            Builder requestBuilder = webResource.request();
            addExtraProperties(requestBuilder);
            addExtraHeaders(requestBuilder);
            requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE);
            response = requestBuilder.put(Entity.entity("{}", MediaType.APPLICATION_JSON_TYPE)); // Jersey2 refuses to handle PUT with no body
            EurekaHttpResponseBuilder<InstanceInfo> eurekaResponseBuilder = anEurekaHttpResponse(response.getStatus(), InstanceInfo.class).headers(headersOf(response));
            if (response.hasEntity()) {
                eurekaResponseBuilder.entity(response.readEntity(InstanceInfo.class));
            }
            return eurekaResponseBuilder.build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP PUT {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        String urlPath = "apps/" + appName + '/' + id + "/status";
        Response response = null;
        try {
            Builder requestBuilder = jerseyClient.target(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString())
                    .request();
            addExtraProperties(requestBuilder);
            addExtraHeaders(requestBuilder);
            response = requestBuilder.put(Entity.entity("{}", MediaType.APPLICATION_JSON_TYPE)); // Jersey2 refuses to handle PUT with no body
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP PUT {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        String urlPath = "apps/" + appName + '/' + id + "/status";
        Response response = null;
        try {
            Builder requestBuilder = jerseyClient.target(serviceUrl)
                    .path(urlPath)
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString())
                    .request();
            addExtraProperties(requestBuilder);
            addExtraHeaders(requestBuilder);
            response = requestBuilder.delete();
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP DELETE {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications(String... regions) {
        return getApplicationsInternal("apps/", regions);
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta(String... regions) {
        return getApplicationsInternal("apps/delta", regions);
    }

    @Override
    public EurekaHttpResponse<Applications> getVip(String vipAddress, String... regions) {
        return getApplicationsInternal("vips/" + vipAddress, regions);
    }

    @Override
    public EurekaHttpResponse<Applications> getSecureVip(String secureVipAddress, String... regions) {
        return getApplicationsInternal("svips/" + secureVipAddress, regions);
    }

    @Override
    public EurekaHttpResponse<Application> getApplication(String appName) {
        String urlPath = "apps/" + appName;
        Response response = null;
        try {
            Builder requestBuilder = jerseyClient.target(serviceUrl).path(urlPath).request();
            addExtraProperties(requestBuilder);
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();

            Application application = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                application = response.readEntity(Application.class);
            }
            return anEurekaHttpResponse(response.getStatus(), application).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP GET {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    private EurekaHttpResponse<Applications> getApplicationsInternal(String urlPath, String[] regions) {
        Response response = null;
        try {
            WebTarget webTarget = jerseyClient.target(serviceUrl).path(urlPath);
            if (regions != null && regions.length > 0) {
                webTarget = webTarget.queryParam("regions", StringUtil.join(regions));
            }
            Builder requestBuilder = webTarget.request();
            addExtraProperties(requestBuilder);
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();

            Applications applications = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                applications = response.readEntity(Applications.class);
            }
            return anEurekaHttpResponse(response.getStatus(), applications).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP GET {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String id) {
        return getInstanceInternal("instances/" + id);
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id) {
        return getInstanceInternal("apps/" + appName + '/' + id);
    }

    private EurekaHttpResponse<InstanceInfo> getInstanceInternal(String urlPath) {
        Response response = null;
        try {
            Builder requestBuilder = jerseyClient.target(serviceUrl).path(urlPath).request();
            addExtraProperties(requestBuilder);
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();

            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.readEntity(InstanceInfo.class);
            }
            return anEurekaHttpResponse(response.getStatus(), infoFromPeer).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey2 HTTP GET {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public void shutdown() {
    }

    protected void addExtraProperties(Builder webResource) {
        if (userName != null) {
            webResource.property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_USERNAME, userName)
                    .property(HttpAuthenticationFeature.HTTP_AUTHENTICATION_PASSWORD, password);
        }
    }

    protected abstract void addExtraHeaders(Builder webResource);

    private static Map<String, String> headersOf(Response response) {
        MultivaluedMap<String, String> jerseyHeaders = response.getStringHeaders();
        if (jerseyHeaders == null || jerseyHeaders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new HashMap<>();
        for (Entry<String, List<String>> entry : jerseyHeaders.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                headers.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return headers;
    }
}

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

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.EurekaHttpResponse.EurekaHttpResponseBuilder;
import com.netflix.discovery.shared.transport.decorator.EurekaHttpClientDecorator.RequestType;
import com.netflix.discovery.shared.transport.jersey.ETagCache;
import com.netflix.discovery.util.StringUtil;
import org.glassfish.jersey.client.JerseyClient;
import org.glassfish.jersey.client.JerseyInvocation.Builder;
import org.glassfish.jersey.client.JerseyWebTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;

/**
 * @author Tomasz Bak
 */
public class Jersey2ApplicationClient implements EurekaHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(Jersey2ApplicationClient.class);

    private final JerseyClient jerseyClient;
    private final String serviceUrl;
    private final boolean allowRedirect;
    private final ETagCache eTagCache;

    public Jersey2ApplicationClient(JerseyClient jerseyClient, String serviceUrl, boolean allowRedirect, boolean useETag) {
        this.jerseyClient = jerseyClient;
        this.serviceUrl = serviceUrl;
        this.allowRedirect = allowRedirect;
        this.eTagCache = useETag ? new ETagCache() : null;
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        String urlPath = "apps/" + info.getAppName();
        Response response = null;
        try {
            Builder resourceBuilder = jerseyClient.target(serviceUrl).path(urlPath).request();
            addExtraHeaders(resourceBuilder);
            response = resourceBuilder
                    .accept(MediaType.APPLICATION_JSON)
                    .acceptEncoding("gzip")
                    .post(Entity.entity(info, MediaType.APPLICATION_JSON_TYPE));
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
            addExtraHeaders(resourceBuilder);
            response = resourceBuilder.delete();
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP DELETE {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
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
            JerseyWebTarget webResource = jerseyClient.target(serviceUrl)
                    .path(urlPath)
                    .queryParam("status", info.getStatus().toString())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                webResource = webResource.queryParam("overriddenstatus", overriddenStatus.name());
            }
            Builder requestBuilder = webResource.request();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.put(Entity.entity("{}", MediaType.APPLICATION_JSON_TYPE)); // Jersey2 refuses to handle PUT with no body
            EurekaHttpResponseBuilder<InstanceInfo> eurekaResponseBuilder = anEurekaHttpResponse(response.getStatus(), InstanceInfo.class).headers(headersOf(response));
            if (response.hasEntity()) {
                eurekaResponseBuilder.entity(response.readEntity(InstanceInfo.class));
            }
            return eurekaResponseBuilder.build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP PUT {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
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
            addExtraHeaders(requestBuilder);
            response = requestBuilder.put(Entity.entity("{}", MediaType.APPLICATION_JSON_TYPE)); // Jersey2 refuses to handle PUT with no body
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP PUT {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
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
            addExtraHeaders(requestBuilder);
            response = requestBuilder.delete();
            return anEurekaHttpResponse(response.getStatus()).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP DELETE {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications(String... regions) {
        return getApplicationsInternal(RequestType.GetApplications, "apps/", regions);
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta(String... regions) {
        return getApplicationsInternal(RequestType.GetDelta, "apps/delta", regions);
    }

    @Override
    public EurekaHttpResponse<Applications> getVip(String vipAddress, String... regions) {
        return getApplicationsInternal(RequestType.GetVip, "vips/" + vipAddress, regions);
    }

    @Override
    public EurekaHttpResponse<Applications> getSecureVip(String secureVipAddress, String... regions) {
        return getApplicationsInternal(RequestType.GetSecureVip, "svips/" + secureVipAddress, regions);
    }

    @Override
    public EurekaHttpResponse<Application> getApplication(String appName) {
        String urlPath = "apps/" + appName;
        Response response = null;
        try {
            Builder requestBuilder = jerseyClient.target(serviceUrl).path(urlPath).request();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();

            Application application = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                application = response.readEntity(Application.class);
            }
            return anEurekaHttpResponse(response.getStatus(), application).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP GET {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    private EurekaHttpResponse<Applications> getApplicationsInternal(RequestType requestType, String urlPath, String[] regions) {
        Response response = null;
        String etag = eTagCache == null ? null : eTagCache.getApplicationsETag(requestType, regions);
        try {
            JerseyWebTarget webTarget = jerseyClient.target(serviceUrl).path(urlPath);
            if (regions != null && regions.length > 0) {
                webTarget = webTarget.queryParam("regions", StringUtil.join(regions));
            }
            Builder requestBuilder = webTarget.request();
            addExtraHeaders(requestBuilder);
            if (etag != null) {
                requestBuilder.header(HttpHeaders.IF_NONE_MATCH, etag);
            }
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();

            Applications applications = null;
            int status = response.getStatus();
            Map<String, String> responseHeaders = headersOf(response);
            if (status == Status.OK.getStatusCode() && response.hasEntity()) {
                applications = response.readEntity(Applications.class);
                if (eTagCache != null) {
                    String newETag = responseHeaders.get(HttpHeaders.ETAG);
                    if(newETag == null) {
                        newETag = responseHeaders.get("Etag"); // This value is returned by Jersey2, not expected ETag
                    }
                    if (newETag != null) {
                        eTagCache.cacheReply(newETag, requestType, regions, applications);
                    }
                }
            } else if (etag != null && status == Status.NOT_MODIFIED.getStatusCode()) {
                status = Status.OK.getStatusCode(); // Overwrite 304 with 200, as we return cached entity
                applications = eTagCache.getCachedApplications(requestType, regions);
            }
            return anEurekaHttpResponse(status, applications).headers(responseHeaders).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP GET {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
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
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get();

            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.readEntity(InstanceInfo.class);
            }
            return anEurekaHttpResponse(response.getStatus(), infoFromPeer).headers(headersOf(response)).build();
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("Jersey HTTP GET {}/{}; statusCode={}", serviceUrl, urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public void shutdown() {

    }

    protected void addExtraHeaders(Builder webResource) {
        if (allowRedirect) {
            webResource.header(DiscoveryClient.HTTP_X_DISCOVERY_ALLOW_REDIRECT, "true");
        }
    }

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

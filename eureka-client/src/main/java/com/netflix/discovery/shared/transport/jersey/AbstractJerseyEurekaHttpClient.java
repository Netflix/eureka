package com.netflix.discovery.shared.transport.jersey;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractJerseyEurekaHttpClient implements EurekaHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(AbstractJerseyEurekaHttpClient.class);

    protected final EurekaJerseyClient jerseyClient;
    protected final String serviceUrl;

    protected AbstractJerseyEurekaHttpClient(EurekaJerseyClient jerseyClient, String serviceUrl) {
        this.jerseyClient = jerseyClient;
        this.serviceUrl = serviceUrl;
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        String urlPath = "apps/" + info.getAppName();
        ClientResponse response = null;
        try {
            Builder resourceBuilder = jerseyClient.getClient().resource(serviceUrl).path(urlPath).getRequestBuilder();
            addExtraHeaders(resourceBuilder);
            response = resourceBuilder
                    .header("Accept-Encoding", "gzip")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, info);
            return EurekaHttpResponse.responseWith(response.getStatus());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[register] Jersey HTTP POST {} with instance {}; statusCode={}", urlPath, info.getId(),
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
        ClientResponse response = null;
        try {
            Builder resourceBuilder = jerseyClient.getClient().resource(serviceUrl).path(urlPath).getRequestBuilder();
            addExtraHeaders(resourceBuilder);
            response = resourceBuilder.delete(ClientResponse.class);
            return EurekaHttpResponse.responseWith(response.getStatus());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[cancel] Jersey HTTP DELETE {}; statusCode={}", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        String urlPath = "apps/" + appName + '/' + id;
        ClientResponse response = null;
        try {
            WebResource webResource = jerseyClient.getClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("status", info.getStatus().toString())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                webResource = webResource.queryParam("overriddenstatus", overriddenStatus.name());
            }
            Builder requestBuilder = webResource.getRequestBuilder();
            addExtraHeaders(requestBuilder);
            return EurekaHttpResponse.responseWith(response.getStatus());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[heartbeat] Jersey HTTP PUT {}; statusCode={}", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        String urlPath = "apps/" + appName + "/" + id + "/status";
        ClientResponse response = null;
        try {
            Builder requestBuilder = jerseyClient.getClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString())
                    .getRequestBuilder();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.put(ClientResponse.class);
            return EurekaHttpResponse.responseWith(response.getStatus());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[statusUpdate] Jersey HTTP PUT {}; statusCode={}", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        String urlPath = "apps/" + appName + '/' + id + "/status";
        ClientResponse response = null;
        try {
            Builder requestBuilder = jerseyClient.getClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString())
                    .getRequestBuilder();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.delete(ClientResponse.class);
            return EurekaHttpResponse.responseWith(response.getStatus());
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[statusOverrideDelete] Jersey HTTP DELETE {}; statusCode={}", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications() {
        String urlPath = "apps/";
        ClientResponse response = null;
        try {
            Builder requestBuilder = jerseyClient.getClient().resource(serviceUrl).path(urlPath).getRequestBuilder();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

            Applications applications = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                applications = response.getEntity(Applications.class);
            }
            return EurekaHttpResponse.responseWith(response.getStatus(), applications);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[getApplications] Jersey HTTP GET {}; statusCode=", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta() {
        String urlPath = "apps/delta";
        ClientResponse response = null;
        try {
            Builder requestBuilder = jerseyClient.getClient().resource(serviceUrl).path(urlPath).getRequestBuilder();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

            Applications applications = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                applications = response.getEntity(Applications.class);
            }
            return EurekaHttpResponse.responseWith(response.getStatus(), applications);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[getDelta] Jersey HTTP GET {}; statusCode=", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id) {
        String urlPath = "apps/" + appName + '/' + id;
        ClientResponse response = null;
        try {
            Builder requestBuilder = jerseyClient.getClient().resource(serviceUrl).path(urlPath).getRequestBuilder();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.getEntity(InstanceInfo.class);
            }
            return EurekaHttpResponse.responseWith(response.getStatus(), infoFromPeer);
        } finally {
            if (logger.isDebugEnabled()) {
                logger.debug("[getInstance] Jersey HTTP GET {}; statusCode=", urlPath, response == null ? "N/A" : response.getStatus());
            }
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public void shutdown() {
        jerseyClient.getClient().destroy();
    }

    protected abstract void addExtraHeaders(Builder webResource);
}

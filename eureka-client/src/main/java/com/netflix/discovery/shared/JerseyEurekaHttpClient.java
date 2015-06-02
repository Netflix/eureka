package com.netflix.discovery.shared;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.client.apache4.ApacheHttpClient4;

/**
 * @author Tomasz Bak
 */
public abstract class JerseyEurekaHttpClient implements EurekaHttpClient {

    protected final String serviceUrl;

    protected JerseyEurekaHttpClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    protected abstract ApacheHttpClient4 getJerseyApacheClient();

    @Override
    public HttpResponse<Void> register(InstanceInfo info) {
        String urlPath = "apps/" + info.getAppName();
        ClientResponse response = null;
        try {
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl).path(urlPath);
            addExtraHeaders(webResource);
            response = webResource
                    .header("Accept-Encoding", "gzip")
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .accept(MediaType.APPLICATION_JSON)
                    .post(ClientResponse.class, info);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<Void> cancel(String appName, String id) {
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + "/" + id;
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl).path(urlPath);
            addExtraHeaders(webResource);
            response = webResource.delete(ClientResponse.class);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + "/" + id;
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("status", info.getStatus().toString())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                webResource = webResource.queryParam("overriddenstatus", overriddenStatus.name());
            }
            addExtraHeaders(webResource);
            response = webResource.accept(MediaType.APPLICATION_JSON_TYPE).put(ClientResponse.class);
            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.getEntity(InstanceInfo.class);

            }
            return HttpResponse.responseWith(response.getStatus(), infoFromPeer);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + "/" + id + "/status";
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            response = webResource.put(ClientResponse.class);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + '/' + id + "/status";
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            addExtraHeaders(webResource);
            response = webResource.delete(ClientResponse.class);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<InstanceInfo> getInstance(String appName, String id) {
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + '/' + id;
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl).path(urlPath);
            addExtraHeaders(webResource);
            response = webResource.accept(MediaType.APPLICATION_JSON_TYPE).get(ClientResponse.class);

            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.OK.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.getEntity(InstanceInfo.class);
            }
            return HttpResponse.responseWith(response.getStatus(), infoFromPeer);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public void shutdown() {
        getJerseyApacheClient().destroy();
    }

    protected void addExtraHeaders(WebResource webResource) {
        // No-op
    }
}

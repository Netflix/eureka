package com.netflix.eureka.cluster;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.EurekaIdentityHeaderFilter;
import com.netflix.discovery.shared.EurekaJerseyClient;
import com.netflix.discovery.shared.EurekaJerseyClient.JerseyClient;
import com.netflix.eureka.CurrentRequestVersion;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.Version;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.GZIPContentEncodingFilter;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class JerseyReplicationClient implements HttpReplicationClient {

    private static final Logger logger = LoggerFactory.getLogger(JerseyReplicationClient.class);

    private final String serviceUrl;

    private final JerseyClient jerseyClient;
    private final ApacheHttpClient4 jerseyApacheClient;

    public JerseyReplicationClient(EurekaServerConfig config, String serviceUrl) {
        this.serviceUrl = serviceUrl;
        String name = getClass().getSimpleName() + ": " + serviceUrl + "apps/: ";

        try {
            String hostname;
            try {
                hostname = new URL(serviceUrl).getHost();
            } catch (MalformedURLException e) {
                hostname = serviceUrl;
            }
            String jerseyClientName = "Discovery-PeerNodeClient-" + hostname;
            if (serviceUrl.startsWith("https://") &&
                    "true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
                jerseyClient = EurekaJerseyClient.createSystemSSLJerseyClient(jerseyClientName,
                        config.getPeerNodeConnectTimeoutMs(),
                        config.getPeerNodeReadTimeoutMs(),
                        config.getPeerNodeTotalConnections(),
                        config.getPeerNodeTotalConnectionsPerHost(),
                        config.getPeerNodeConnectionIdleTimeoutSeconds());
            } else {
                jerseyClient = EurekaJerseyClient.createJerseyClient(jerseyClientName,
                        config.getPeerNodeConnectTimeoutMs(),
                        config.getPeerNodeReadTimeoutMs(),
                        config.getPeerNodeTotalConnections(),
                        config.getPeerNodeTotalConnectionsPerHost(),
                        config.getPeerNodeConnectionIdleTimeoutSeconds());
            }
            jerseyApacheClient = jerseyClient.getClient();
            jerseyApacheClient.addFilter(new GZIPContentEncodingFilter(true));
        } catch (Throwable e) {
            throw new RuntimeException("Cannot Create new Replica Node :" + name, e);
        }

        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("Cannot find localhost ip", e);
        }
        EurekaServerIdentity identity = new EurekaServerIdentity(ip);
        jerseyApacheClient.addFilter(new EurekaIdentityHeaderFilter(identity));
    }

    @Override
    public HttpResponse<Void> register(InstanceInfo info) {
        CurrentRequestVersion.set(Version.V2);
        String urlPath = "apps/" + info.getAppName();
        ClientResponse response = null;
        try {
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
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
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .delete(ClientResponse.class);
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
            WebResource r = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("status", info.getStatus().toString())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                r = r.queryParam("overriddenstatus", overriddenStatus.name());
            }
            response = r.accept(MediaType.APPLICATION_JSON_TYPE)
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .put(ClientResponse.class);
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
    public HttpResponse<Void> statusUpdate(String asgName, ASGStatus newStatus) {
        ClientResponse response = null;
        try {
            String urlPath = "asg/" + asgName + "/status";
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .put(ClientResponse.class);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        CurrentRequestVersion.set(Version.V2);
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + "/" + id + "/status";
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString())
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .put(ClientResponse.class);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        CurrentRequestVersion.set(Version.V2);
        ClientResponse response = null;
        try {
            String urlPath = "apps/" + appName + '/' + id + "/status";
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString())
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .delete(ClientResponse.class);
            return HttpResponse.responseWith(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public HttpResponse<ReplicationListResponse> submitBatchUpdates(ReplicationList replicationList) {
        ClientResponse response = null;
        try {
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(PeerEurekaNode.BATCH_URL_PATH)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .post(ClientResponse.class, replicationList);
            if (!isSuccess(response.getStatus())) {
                return HttpResponse.responseWith(response.getStatus());
            }
            ReplicationListResponse batchResponse = response.getEntity(ReplicationListResponse.class);
            return HttpResponse.responseWith(response.getStatus(), batchResponse);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public void shutdown() {
        jerseyClient.destroyResources();
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}

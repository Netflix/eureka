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
import com.netflix.discovery.shared.JerseyClient;
import com.netflix.discovery.shared.JerseyClientConfigBuilder;
import com.netflix.discovery.shared.JerseyEurekaHttpClient;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public class JerseyReplicationClient extends JerseyEurekaHttpClient implements HttpReplicationClient {

    private static final Logger logger = LoggerFactory.getLogger(JerseyReplicationClient.class);

    private final JerseyClient jerseyClient;
    private final ApacheHttpClient4 jerseyApacheClient;

    public JerseyReplicationClient(EurekaServerConfig config, String serviceUrl) {
        super(serviceUrl);
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
                jerseyClient = new JerseyClient(
                        config.getPeerNodeConnectTimeoutMs(),
                        config.getPeerNodeReadTimeoutMs(),
                        config.getPeerNodeConnectionIdleTimeoutSeconds(),
                        JerseyClientConfigBuilder.newSystemSSLClientConfigBuilder()
                                .withClientName(jerseyClientName)
                                .withMaxConnectionsPerHost(config.getPeerNodeTotalConnectionsPerHost())
                                .withMaxTotalConnections(config.getPeerNodeTotalConnections())
                                .build()
                );
            } else {
                jerseyClient = new JerseyClient(
                        config.getPeerNodeConnectTimeoutMs(),
                        config.getPeerNodeReadTimeoutMs(),
                        config.getPeerNodeConnectionIdleTimeoutSeconds(),
                        JerseyClientConfigBuilder.newClientConfigBuilder()
                                .withClientName(jerseyClientName)
                                .withMaxConnectionsPerHost(config.getPeerNodeTotalConnectionsPerHost())
                                .withMaxTotalConnections(config.getPeerNodeTotalConnections())
                                .build()
                );
            }
            jerseyApacheClient = jerseyClient.getClient();
            jerseyApacheClient.addFilter(new DynamicGZIPContentEncodingFilter(config));
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
    protected ApacheHttpClient4 getJerseyApacheClient() {
        return jerseyApacheClient;
    }

    @Override
    protected void addExtraHeaders(Builder webResource) {
        webResource.header(PeerEurekaNode.HEADER_REPLICATION, "true");
    }

    /**
     * Compared to regular heartbeat, in the replication channel the server may return a more up to date
     * instance copy.
     */
    @Override
    public HttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        String urlPath = "apps/" + appName + '/' + id;
        ClientResponse response = null;
        try {
            WebResource webResource = getJerseyApacheClient().resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("status", info.getStatus().toString())
                    .queryParam("lastDirtyTimestamp", info.getLastDirtyTimestamp().toString());
            if (overriddenStatus != null) {
                webResource = webResource.queryParam("overriddenstatus", overriddenStatus.name());
            }
            Builder requestBuilder = webResource.getRequestBuilder();
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).put(ClientResponse.class);
            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.CONFLICT.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.getEntity(InstanceInfo.class);
            }
            return HttpResponse.responseWith(response.getStatus(), infoFromPeer);
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
        super.shutdown();
        jerseyClient.destroyResources();
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}

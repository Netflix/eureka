package com.netflix.eureka.transport;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.EurekaIdentityHeaderFilter;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.jersey.AbstractJerseyEurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.cluster.DynamicGZIPContentEncodingFilter;
import com.netflix.eureka.cluster.HttpReplicationClient;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.eureka.resources.ServerCodecs;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;

/**
 * @author Tomasz Bak
 */
public class JerseyReplicationClient extends AbstractJerseyEurekaHttpClient implements HttpReplicationClient {

    private static final Logger logger = LoggerFactory.getLogger(JerseyReplicationClient.class);

    private final EurekaJerseyClient jerseyClient;
    private final ApacheHttpClient4 jerseyApacheClient;

    public JerseyReplicationClient(EurekaJerseyClient jerseyClient, String serviceUrl) {
        super(jerseyClient.getClient(), serviceUrl);
        this.jerseyClient = jerseyClient;
        this.jerseyApacheClient = jerseyClient.getClient();
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
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).put(ClientResponse.class);
            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.CONFLICT.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.getEntity(InstanceInfo.class);
            }
            return anEurekaHttpResponse(response.getStatus(), infoFromPeer).type(MediaType.APPLICATION_JSON_TYPE).build();
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
    public EurekaHttpResponse<Void> statusUpdate(String asgName, ASGStatus newStatus) {
        ClientResponse response = null;
        try {
            String urlPath = "asg/" + asgName + "/status";
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .put(ClientResponse.class);
            return EurekaHttpResponse.status(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<ReplicationListResponse> submitBatchUpdates(ReplicationList replicationList) {
        ClientResponse response = null;
        try {
            response = jerseyApacheClient.resource(serviceUrl)
                    .path(PeerEurekaNode.BATCH_URL_PATH)
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .post(ClientResponse.class, replicationList);
            if (!isSuccess(response.getStatus())) {
                return anEurekaHttpResponse(response.getStatus(), ReplicationListResponse.class).build();
            }
            ReplicationListResponse batchResponse = response.getEntity(ReplicationListResponse.class);
            return anEurekaHttpResponse(response.getStatus(), batchResponse).type(MediaType.APPLICATION_JSON_TYPE).build();
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

    public static JerseyReplicationClient createReplicationClient(EurekaServerConfig config, ServerCodecs serverCodecs, String serviceUrl) {
        String name = JerseyReplicationClient.class.getSimpleName() + ": " + serviceUrl + "apps/: ";

        EurekaJerseyClient jerseyClient;
        try {
            String hostname;
            try {
                hostname = new URL(serviceUrl).getHost();
            } catch (MalformedURLException e) {
                hostname = serviceUrl;
            }

            String jerseyClientName = "Discovery-PeerNodeClient-" + hostname;
            EurekaJerseyClientBuilder clientBuilder = new EurekaJerseyClientBuilder()
                    .withClientName(jerseyClientName)
                    .withUserAgent("Java-EurekaClient-Replication")
                    .withEncoderWrapper(serverCodecs.getFullJsonCodec())
                    .withDecoderWrapper(serverCodecs.getFullJsonCodec())
                    .withConnectionTimeout(config.getPeerNodeConnectTimeoutMs())
                    .withReadTimeout(config.getPeerNodeReadTimeoutMs())
                    .withMaxConnectionsPerHost(config.getPeerNodeTotalConnectionsPerHost())
                    .withMaxTotalConnections(config.getPeerNodeTotalConnections())
                    .withConnectionIdleTimeout(config.getPeerNodeConnectionIdleTimeoutSeconds());

            if (serviceUrl.startsWith("https://") &&
                    "true".equals(System.getProperty("com.netflix.eureka.shouldSSLConnectionsUseSystemSocketFactory"))) {
                clientBuilder.withSystemSSLConfiguration();
            }
            jerseyClient = clientBuilder.build();
        } catch (Throwable e) {
            throw new RuntimeException("Cannot Create new Replica Node :" + name, e);
        }

        String ip = null;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.warn("Cannot find localhost ip", e);
        }

        ApacheHttpClient4 jerseyApacheClient = jerseyClient.getClient();
        jerseyApacheClient.addFilter(new DynamicGZIPContentEncodingFilter(config));

        EurekaServerIdentity identity = new EurekaServerIdentity(ip);
        jerseyApacheClient.addFilter(new EurekaIdentityHeaderFilter(identity));

        return new JerseyReplicationClient(jerseyClient, serviceUrl);
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}

package com.netflix.eureka.transport;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.jersey2.AbstractJersey2EurekaHttpClient;
import com.netflix.discovery.shared.transport.jersey2.EurekaIdentityHeaderFilter;
import com.netflix.discovery.shared.transport.jersey2.EurekaJersey2Client;
import com.netflix.discovery.shared.transport.jersey2.EurekaJersey2ClientImpl;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerIdentity;
import com.netflix.eureka.cluster.HttpReplicationClient;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.eureka.resources.ServerCodecs;

/**
 * @author Tomasz Bak
 */
public class Jersey2ReplicationClient extends AbstractJersey2EurekaHttpClient implements HttpReplicationClient {

    private static final Logger logger = LoggerFactory.getLogger(Jersey2ReplicationClient.class);

    private final EurekaJersey2Client eurekaJersey2Client;

    public Jersey2ReplicationClient(EurekaJersey2Client eurekaJersey2Client, String serviceUrl) {
        super(eurekaJersey2Client.getClient(), serviceUrl);
        this.eurekaJersey2Client = eurekaJersey2Client;
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
            addExtraHeaders(requestBuilder);
            response = requestBuilder.accept(MediaType.APPLICATION_JSON_TYPE).put(Entity.entity("{}", MediaType.APPLICATION_JSON_TYPE)); // Jersey2 refuses to handle PUT with no body
            InstanceInfo infoFromPeer = null;
            if (response.getStatus() == Status.CONFLICT.getStatusCode() && response.hasEntity()) {
                infoFromPeer = response.readEntity(InstanceInfo.class);
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
        Response response = null;
        try {
            String urlPath = "asg/" + asgName + "/status";
            response = jerseyClient.target(serviceUrl)
                    .path(urlPath)
                    .queryParam("value", newStatus.name())
                    .request()
                    .header(PeerEurekaNode.HEADER_REPLICATION, "true")
                    .put(Entity.text(""));
            return EurekaHttpResponse.status(response.getStatus());
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    public EurekaHttpResponse<ReplicationListResponse> submitBatchUpdates(ReplicationList replicationList) {
        Response response = null;
        try {
            response = jerseyClient.target(serviceUrl)
                    .path(PeerEurekaNode.BATCH_URL_PATH)
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .post(Entity.json(replicationList));
            if (!isSuccess(response.getStatus())) {
                return anEurekaHttpResponse(response.getStatus(), ReplicationListResponse.class).build();
            }
            ReplicationListResponse batchResponse = response.readEntity(ReplicationListResponse.class);
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
        eurekaJersey2Client.destroyResources();
    }

    public static Jersey2ReplicationClient createReplicationClient(EurekaServerConfig config, ServerCodecs serverCodecs, String serviceUrl) {
        String name = Jersey2ReplicationClient.class.getSimpleName() + ": " + serviceUrl + "apps/: ";

        EurekaJersey2Client jerseyClient;
        try {
            String hostname;
            try {
                hostname = new URL(serviceUrl).getHost();
            } catch (MalformedURLException e) {
                hostname = serviceUrl;
            }

            String jerseyClientName = "Discovery-PeerNodeClient-" + hostname;
            EurekaJersey2ClientImpl.EurekaJersey2ClientBuilder clientBuilder = new EurekaJersey2ClientImpl.EurekaJersey2ClientBuilder()
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

        Client jerseyApacheClient = jerseyClient.getClient();
        jerseyApacheClient.register(new Jersey2DynamicGZIPContentEncodingFilter(config));

        EurekaServerIdentity identity = new EurekaServerIdentity(ip);
        jerseyApacheClient.register(new EurekaIdentityHeaderFilter(identity));

        return new Jersey2ReplicationClient(jerseyClient, serviceUrl);
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}

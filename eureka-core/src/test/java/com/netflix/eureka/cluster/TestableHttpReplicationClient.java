package com.netflix.eureka.cluster;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.resources.ASGResource.ASGStatus;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;

/**
 * This stub implementation is primarily useful for batch updates, and complex failure scenarios.
 * Using mock would results in too convoluted code.
 *
 * @author Tomasz Bak
 */
public class TestableHttpReplicationClient implements HttpReplicationClient {

    private int[] networkStatusCodes;
    private InstanceInfo instanceInfoFromPeer;
    private int networkFailuresRepeatCount;
    private int readtimeOutRepeatCount;


    private int batchStatusCode;

    private final AtomicInteger callCounter = new AtomicInteger();
    private final AtomicInteger networkFailureCounter = new AtomicInteger();
    private final AtomicInteger readTimeOutCounter = new AtomicInteger();

    private long processingDelayMs;
    

    private final BlockingQueue<HandledRequest> handledRequests = new LinkedBlockingQueue<>();

    public void withNetworkStatusCode(int... networkStatusCodes) {
        this.networkStatusCodes = networkStatusCodes;
    }

    public void withInstanceInfo(InstanceInfo instanceInfoFromPeer) {
        this.instanceInfoFromPeer = instanceInfoFromPeer;
    }

    public void withBatchReply(int batchStatusCode) {
        this.batchStatusCode = batchStatusCode;
    }

    public void withNetworkError(int networkFailuresRepeatCount) {
        this.networkFailuresRepeatCount = networkFailuresRepeatCount;
    }

    public void withReadtimeOut(int readtimeOutRepeatCount) {
        this.readtimeOutRepeatCount = readtimeOutRepeatCount;
    }

    public void withProcessingDelay(long processingDelay, TimeUnit timeUnit) {
        this.processingDelayMs = timeUnit.toMillis(processingDelay);
    }

    public HandledRequest nextHandledRequest(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return handledRequests.poll(timeout, timeUnit);
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        handledRequests.add(new HandledRequest(RequestType.Register, info));
        return EurekaHttpResponse.status(networkStatusCodes[callCounter.getAndIncrement()]);
    }

    @Override
    public EurekaHttpResponse<Void> cancel(String appName, String id) {
        handledRequests.add(new HandledRequest(RequestType.Cancel, id));
        return EurekaHttpResponse.status(networkStatusCodes[callCounter.getAndIncrement()]);
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        handledRequests.add(new HandledRequest(RequestType.Heartbeat, instanceInfoFromPeer));
        int statusCode = networkStatusCodes[callCounter.getAndIncrement()];
        return anEurekaHttpResponse(statusCode, instanceInfoFromPeer).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String asgName, ASGStatus newStatus) {
        handledRequests.add(new HandledRequest(RequestType.AsgStatusUpdate, newStatus));
        return EurekaHttpResponse.status(networkStatusCodes[callCounter.getAndIncrement()]);
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        handledRequests.add(new HandledRequest(RequestType.StatusUpdate, newStatus));
        return EurekaHttpResponse.status(networkStatusCodes[callCounter.getAndIncrement()]);
    }

    @Override
    public EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        handledRequests.add(new HandledRequest(RequestType.DeleteStatusOverride, null));
        return EurekaHttpResponse.status(networkStatusCodes[callCounter.getAndIncrement()]);
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications(String... regions) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta(String... regions) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<Applications> getVip(String vipAddress, String... regions) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<Applications> getSecureVip(String secureVipAddress, String... regions) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<Application> getApplication(String appName) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String id) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id) {
        throw new IllegalStateException("method not supported");
    }

    @Override
    public EurekaHttpResponse<ReplicationListResponse> submitBatchUpdates(ReplicationList replicationList) {
    	
        if (readTimeOutCounter.get() < readtimeOutRepeatCount) {
            readTimeOutCounter.incrementAndGet();
            throw new RuntimeException(new SocketTimeoutException("Read timed out"));
        }
    	
    	
        if (networkFailureCounter.get() < networkFailuresRepeatCount) {
            networkFailureCounter.incrementAndGet();
            throw new RuntimeException(new IOException("simulated network failure"));
        }

        if (processingDelayMs > 0) {
            try {
                Thread.sleep(processingDelayMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        List<ReplicationInstanceResponse> responseList = new ArrayList<>();
        responseList.add(new ReplicationInstanceResponse(batchStatusCode, instanceInfoFromPeer));
        ReplicationListResponse replicationListResponse = new ReplicationListResponse(responseList);

        handledRequests.add(new HandledRequest(RequestType.Batch, replicationList));

        int statusCode = networkStatusCodes[callCounter.getAndIncrement()];
        return anEurekaHttpResponse(statusCode, replicationListResponse).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    @Override
    public void shutdown() {
    }

    public enum RequestType {Heartbeat, Register, Cancel, StatusUpdate, DeleteStatusOverride, AsgStatusUpdate, Batch}

    public static class HandledRequest {
        private final RequestType requestType;
        private final Object data;

        public HandledRequest(RequestType requestType, Object data) {
            this.requestType = requestType;
            this.data = data;
        }

        public RequestType getRequestType() {
            return requestType;
        }

        public Object getData() {
            return data;
        }
    }
}

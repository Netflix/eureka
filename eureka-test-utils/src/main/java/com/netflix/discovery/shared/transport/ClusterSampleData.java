package com.netflix.discovery.shared.transport;

import java.util.Iterator;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Collection of functions to create different kinds of configuration/data.
 *
 * @author Tomasz Bak
 */
public final class ClusterSampleData {

    public static final long REPLICATION_EXPIRY_TIME_MS = 100;

    public static final long RETRY_SLEEP_TIME_MS = 1;

    public static final long SERVER_UNAVAILABLE_SLEEP_TIME_MS = 1;

    public static final long EUREKA_NODES_UPDATE_INTERVAL_MS = 10;

    private ClusterSampleData() {
    }

    public static EurekaServerConfig newEurekaServerConfig() {
        EurekaServerConfig config = mock(EurekaServerConfig.class);

        // Cluster management related
        when(config.getPeerEurekaNodesUpdateIntervalMs()).thenReturn((int) EUREKA_NODES_UPDATE_INTERVAL_MS);

        // Replication logic related
        when(config.shouldSyncWhenTimestampDiffers()).thenReturn(true);
        when(config.getMaxTimeForReplication()).thenReturn((int) REPLICATION_EXPIRY_TIME_MS);
        when(config.getMaxElementsInPeerReplicationPool()).thenReturn(10);
        when(config.getMaxElementsInStatusReplicationPool()).thenReturn(10);
        when(config.getMaxThreadsForPeerReplication()).thenReturn(1);
        when(config.getMaxThreadsForStatusReplication()).thenReturn(1);

        return config;
    }

    public static InstanceInfo newInstanceInfo(int index) {
        Iterator<InstanceInfo> instanceGenerator = InstanceInfoGenerator.newBuilder(10, 10)
                .withMetaData(true).build().serviceIterator();
        // Skip to the requested index
        for (int i = 0; i < index; i++) {
            instanceGenerator.next();
        }
        return instanceGenerator.next();
    }

    public static ReplicationInstance newReplicationInstance() {
        return newReplicationInstanceOf(Action.Register, newInstanceInfo(0));
    }

    public static ReplicationInstance newReplicationInstanceOf(Action action, InstanceInfo instance) {
        switch (action) {
            case Register:
                return new ReplicationInstance(
                        instance.getAppName(),
                        instance.getId(),
                        System.currentTimeMillis(),
                        null,
                        instance.getStatus().name(),
                        instance,
                        action
                );
            case Cancel:
                return new ReplicationInstance(
                        instance.getAppName(),
                        instance.getId(),
                        System.currentTimeMillis(),
                        null,
                        null,
                        null,
                        action
                );
            case Heartbeat:
                return new ReplicationInstance(
                        instance.getAppName(),
                        instance.getId(),
                        System.currentTimeMillis(),
                        InstanceStatus.OUT_OF_SERVICE.name(),
                        instance.getStatus().name(),
                        instance,
                        action
                );
            case StatusUpdate:
                return new ReplicationInstance(
                        instance.getAppName(),
                        instance.getId(),
                        System.currentTimeMillis(),
                        InstanceStatus.OUT_OF_SERVICE.name(),
                        null,
                        null,
                        action
                );
            case DeleteStatusOverride:
                return new ReplicationInstance(
                        instance.getAppName(),
                        instance.getId(),
                        System.currentTimeMillis(),
                        InstanceStatus.OUT_OF_SERVICE.name(),
                        null,
                        null,
                        action
                );
        }
        throw new IllegalStateException("Unexpected action " + action);
    }

    public static ReplicationInstanceResponse newReplicationInstanceResponse(boolean withInstanceInfo) {
        return new ReplicationInstanceResponse(200, withInstanceInfo ? newInstanceInfo(1) : null);
    }
}

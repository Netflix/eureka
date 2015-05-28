package com.netflix.eureka.cluster;

import java.util.Iterator;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.InstanceInfoGenerator;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Collection of functions to create different kinds of configuration/data.
 *
 * @author Tomasz Bak
 */
public final class ReplicationSampleData {

    public static final long REPLICATION_EXPIRY_TIME_MS = 100;

    public static final long MAX_PROCESSING_DELAY_MS = 10;

    public static final long RETRY_SLEEP_TIME_MS = 1;

    public static final long SERVER_UNAVAILABLE_SLEEP_TIME_MS = 1;

    private ReplicationSampleData() {
    }

    public static EurekaServerConfig newEurekaServerConfig(boolean batchingEnabled) {
        EurekaServerConfig config = mock(EurekaServerConfig.class);
        when(config.shouldSyncWhenTimestampDiffers()).thenReturn(true);
        when(config.getMaxTimeForReplication()).thenReturn((int) REPLICATION_EXPIRY_TIME_MS);
        when(config.getMaxElementsInPeerReplicationPool()).thenReturn(10);
        when(config.getMinThreadsForPeerReplication()).thenReturn(1);
        when(config.getMaxThreadsForPeerReplication()).thenReturn(1);
        when(config.shouldBatchReplication()).thenReturn(batchingEnabled);
        return config;
    }

    public static InstanceInfo newInstanceInfo(int index) {
        Iterator<InstanceInfo> instanceGenerator = new InstanceInfoGenerator(10, 10, true).serviceIterator();
        // Skip to the requested index
        for (int i = 0; i < index; i++) {
            instanceGenerator.next();
        }
        return instanceGenerator.next();
    }

    public static ReplicationInstance newReplicationInstance() {
        InstanceInfo instance1 = newInstanceInfo(1);
        return new ReplicationInstance(
                instance1.getAppName(),
                instance1.getId(),
                System.currentTimeMillis(),
                InstanceStatus.OUT_OF_SERVICE.name(),
                instance1.getStatus().name(),
                instance1,
                Action.Register
        );
    }

    public static ReplicationInstanceResponse newReplicationInstanceResponse(boolean withInstanceInfo) {
        return new ReplicationInstanceResponse(200, withInstanceInfo ? newInstanceInfo(1) : null);
    }
}

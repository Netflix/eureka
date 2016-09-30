package com.netflix.eureka.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;

public class StatusUtilTest {
    
    @Test
    public void testGetStatusInfoHealthy() {
        StatusUtil statusUtil = getStatusUtil(3, 3, 2);
        assertTrue(statusUtil.getStatusInfo().isHealthy());
    }
    
    @Test
    public void testGetStatusInfoUnhealthy() {
        StatusUtil statusUtil = getStatusUtil(5, 3, 4);
        assertFalse(statusUtil.getStatusInfo().isHealthy());
    }
    
    @Test
    public void testGetStatusInfoUnsetHealth() {
        StatusUtil statusUtil = getStatusUtil(5, 3, -1);
        StatusInfo statusInfo = statusUtil.getStatusInfo();
        
        try {
            statusInfo.isHealthy();
        } catch (NullPointerException e) {
            // Expected that the healthy flag is not set when the minimum value is -1
            return;
        }
        
        fail("Excpected NPE to be thrown when healthy threshold is not set");
    }
    
    /**
     * @param replicas the number of replicas to mock
     * @param instances the number of instances to mock
     * @param minimum the minimum number of peers
     * @return the status utility with the mocked replicas/instances
     */
    private StatusUtil getStatusUtil(int replicas, int instances, int minimum) {
        EurekaServerContext mockEurekaServerContext = mock(EurekaServerContext.class);

        List<InstanceInfo> mockInstanceInfos = getMockInstanceInfos(instances);
        Application mockApplication = mock(Application.class);
        when(mockApplication.getInstances()).thenReturn(mockInstanceInfos);
        
        ApplicationInfoManager mockAppInfoManager = mock(ApplicationInfoManager.class);
        when(mockAppInfoManager.getInfo()).thenReturn(mockInstanceInfos.get(0));
        when(mockEurekaServerContext.getApplicationInfoManager()).thenReturn(mockAppInfoManager);
        
        PeerAwareInstanceRegistry mockRegistry = mock(PeerAwareInstanceRegistry.class);
        when(mockRegistry.getApplication("stuff", false)).thenReturn(mockApplication);
        when(mockEurekaServerContext.getRegistry()).thenReturn(mockRegistry);
        
        List<PeerEurekaNode> mockNodes = getMockNodes(replicas);
        
        EurekaTransportConfig mockTransportConfig = mock(EurekaTransportConfig.class);
        when(mockTransportConfig.applicationsResolverUseIp()).thenReturn(false);
        EurekaClientConfig mockClientConfig = mock(EurekaClientConfig.class);
        when(mockClientConfig.getTransportConfig()).thenReturn(mockTransportConfig);
        
        EurekaServerConfig mockServerConfig = mock(EurekaServerConfig.class);
        when(mockServerConfig.getHealthStatusMinNumberOfAvailablePeers()).thenReturn(minimum);
        
        PeerEurekaNodes peerEurekaNodes = new PeerEurekaNodes(mockRegistry, mockServerConfig, mockClientConfig, null, mockAppInfoManager);
        PeerEurekaNodes spyPeerEurekaNodes = spy(peerEurekaNodes);
        when(spyPeerEurekaNodes.getPeerEurekaNodes()).thenReturn(mockNodes);
        
        when(mockEurekaServerContext.getPeerEurekaNodes()).thenReturn(spyPeerEurekaNodes);
        
        return new StatusUtil(mockEurekaServerContext);
    }

    List<InstanceInfo> getMockInstanceInfos(int size) {
        List<InstanceInfo> instances = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            InstanceInfo mockInstance = mock(InstanceInfo.class);
            when(mockInstance.getHostName()).thenReturn(String.valueOf(i));
            when(mockInstance.getIPAddr()).thenReturn(String.valueOf(i));
            when(mockInstance.getAppName()).thenReturn("stuff");
            instances.add(mockInstance);
        }
        
        return instances;
    }
    
    List<PeerEurekaNode> getMockNodes(int size) {
        List<PeerEurekaNode> nodes = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            PeerEurekaNode mockNode = mock(PeerEurekaNode.class);
            when(mockNode.getServiceUrl()).thenReturn(String.format("http://%d:8080/v2", i));
            nodes.add(mockNode);
        }
        
        return nodes;
    }
}

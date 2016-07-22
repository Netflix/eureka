package com.netflix.eureka.util;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.aws.AwsAsgUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * @author David Liu
 */
public class AwsAsgUtilTest {

    private ApplicationInfoManager applicationInfoManager;
    private PeerAwareInstanceRegistry registry;
    private DiscoveryClient client;
    private AwsAsgUtil awsAsgUtil;
    private InstanceInfo instanceInfo;

    @Before
    public void setUp() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("eureka.awsAccessId", "fakeId");
        ConfigurationManager.getConfigInstance().setProperty("eureka.awsSecretKey", "fakeKey");

        AmazonInfo dataCenterInfo = mock(AmazonInfo.class);

        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        builder.setIPAddr("10.10.101.00");
        builder.setHostName("fakeHost");
        builder.setAppName("fake-" + UUID.randomUUID());
        builder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        builder.setDataCenterInfo(dataCenterInfo);
        instanceInfo = builder.build();

        applicationInfoManager = new ApplicationInfoManager(new MyDataCenterInstanceConfig(), instanceInfo);
        DefaultEurekaClientConfig clientConfig = new DefaultEurekaClientConfig();
        // setup config in advance, used in initialize converter
        client = mock(DiscoveryClient.class);
        registry = mock(PeerAwareInstanceRegistry.class);

        awsAsgUtil = spy(new AwsAsgUtil(serverConfig, clientConfig, registry));
    }

    @After
    public void tearDown() throws Exception {
        ConfigurationManager.getConfigInstance().clear();
    }

    @Test
    public void testDefaultAsgStatus() {
        Assert.assertEquals(true, awsAsgUtil.isASGEnabled(instanceInfo));
    }

    @Test
    public void testAsyncLoadingFromCache() {
        // TODO
    }
}

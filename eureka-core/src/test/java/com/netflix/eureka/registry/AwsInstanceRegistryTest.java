package com.netflix.eureka.registry;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.resources.ServerCodecs;
import org.junit.Test;


/**
 * Created by Nikos Michalakis on 7/14/16.
 */
public class AwsInstanceRegistryTest extends InstanceRegistryTest {

    @Test
    public void testOverridesWithAsgEnabledThenDisabled() {
        // Regular registration first
        InstanceInfo myInstance = createLocalUpInstanceWithAsg(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);

        // Now we disable the ASG and we should expect OUT_OF_SERVICE status.
        ((AwsInstanceRegistry) registry).getAwsAsgUtil().setStatus(myInstance.getASGName(), false);

        myInstance = createLocalUpInstanceWithAsg(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.OUT_OF_SERVICE);

        // Now we re-enable the ASG and we should expect UP status.
        ((AwsInstanceRegistry) registry).getAwsAsgUtil().setStatus(myInstance.getASGName(), true);

        myInstance = createLocalUpInstanceWithAsg(LOCAL_REGION_INSTANCE_1_HOSTNAME);
        registerInstanceLocally(myInstance);
        verifyLocalInstanceStatus(myInstance.getId(), InstanceStatus.UP);
    }

    private static InstanceInfo createLocalUpInstanceWithAsg(String hostname) {
        InstanceInfo.Builder instanceBuilder = InstanceInfo.Builder.newBuilder();
        instanceBuilder.setAppName(LOCAL_REGION_APP_NAME);
        instanceBuilder.setHostName(hostname);
        instanceBuilder.setIPAddr("10.10.101.1");
        instanceBuilder.setDataCenterInfo(getAmazonInfo(hostname));
        instanceBuilder.setLeaseInfo(LeaseInfo.Builder.newBuilder().build());
        instanceBuilder.setStatus(InstanceStatus.UP);
        instanceBuilder.setASGName("ASG-YO-HO");
        return instanceBuilder.build();
    }

    private static AmazonInfo getAmazonInfo(String instanceHostName) {
        AmazonInfo.Builder azBuilder = AmazonInfo.Builder.newBuilder();
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.availabilityZone, "us-east-1a");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.instanceId, instanceHostName);
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.amiId, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.instanceType, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.localIpv4, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.publicIpv4, "XXX");
        azBuilder.addMetadata(AmazonInfo.MetaDataKey.publicHostname, instanceHostName);
        return azBuilder.build();
    }

    @Override
    protected DataCenterInfo getDataCenterInfo() {
        return getAmazonInfo(LOCAL_REGION_INSTANCE_1_HOSTNAME);
    }

    @Override
    protected PeerAwareInstanceRegistryImpl makePeerAwareInstanceRegistry(EurekaServerConfig serverConfig,
                                                                      EurekaClientConfig clientConfig,
                                                                      ServerCodecs serverCodecs,
                                                                      EurekaClient eurekaClient) {
        return new TestAwsInstanceRegistry(serverConfig, clientConfig, serverCodecs, eurekaClient);
    }


    private static class TestAwsInstanceRegistry extends AwsInstanceRegistry {

        public TestAwsInstanceRegistry(EurekaServerConfig serverConfig,
                                             EurekaClientConfig clientConfig,
                                             ServerCodecs serverCodecs,
                                             EurekaClient eurekaClient) {
            super(serverConfig, clientConfig, serverCodecs, eurekaClient);
        }

        @Override
        public boolean isLeaseExpirationEnabled() {
            return false;
        }

        @Override
        public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
            return null;
        }
    }

}

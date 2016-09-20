package com.netflix.appinfo;

import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.appinfo.AmazonInfo.MetaDataKey.localIpv4;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.publicHostname;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ApplicationInfoManagerTest {

    private CloudInstanceConfig config;
    private String dummyDefault = "dummyDefault";
    private InstanceInfo instanceInfo;
    private ApplicationInfoManager applicationInfoManager;

    @Before
    public void setUp() {
        AmazonInfo initialAmazonInfo = AmazonInfo.Builder.newBuilder().build();

        config = spy(new CloudInstanceConfig(initialAmazonInfo));
        instanceInfo = InstanceInfoGenerator.takeOne();
        this.applicationInfoManager = new ApplicationInfoManager(config, instanceInfo, null);
        when(config.getDefaultAddressResolutionOrder()).thenReturn(new String[]{
                publicHostname.name(),
                localIpv4.name()
        });
        when(config.getHostName(anyBoolean())).thenReturn(dummyDefault);
    }

    @Test
    public void testRefreshDataCenterInfoWithAmazonInfo() {
        String newPublicHostname = "newValue";
        assertThat(instanceInfo.getHostName(), is(not(newPublicHostname)));

        ((AmazonInfo)config.getDataCenterInfo()).getMetadata().put(publicHostname.getName(), newPublicHostname);
        applicationInfoManager.refreshDataCenterInfoIfRequired();

        assertThat(instanceInfo.getHostName(), is(newPublicHostname));
    }

    @Test
    public void testCustomInstanceStatusMapper() {
        ApplicationInfoManager.OptionalArgs optionalArgs = new ApplicationInfoManager.OptionalArgs();
        optionalArgs.setInstanceStatusMapper(new ApplicationInfoManager.InstanceStatusMapper() {
            @Override
            public InstanceInfo.InstanceStatus map(InstanceInfo.InstanceStatus prev) {
                return InstanceInfo.InstanceStatus.UNKNOWN;
            }
        });

        applicationInfoManager = new ApplicationInfoManager(config, instanceInfo, optionalArgs);
        InstanceInfo.InstanceStatus existingStatus = applicationInfoManager.getInfo().getStatus();
        assertNotEquals(existingStatus, InstanceInfo.InstanceStatus.UNKNOWN);

        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UNKNOWN);
        existingStatus = applicationInfoManager.getInfo().getStatus();
        assertEquals(existingStatus, InstanceInfo.InstanceStatus.UNKNOWN);
    }

    @Test
    public void testNullResultInstanceStatusMapper() {
        ApplicationInfoManager.OptionalArgs optionalArgs = new ApplicationInfoManager.OptionalArgs();
        optionalArgs.setInstanceStatusMapper(new ApplicationInfoManager.InstanceStatusMapper() {
            @Override
            public InstanceInfo.InstanceStatus map(InstanceInfo.InstanceStatus prev) {
                return null;
            }
        });

        applicationInfoManager = new ApplicationInfoManager(config, instanceInfo, optionalArgs);
        InstanceInfo.InstanceStatus existingStatus1 = applicationInfoManager.getInfo().getStatus();
        assertNotEquals(existingStatus1, InstanceInfo.InstanceStatus.UNKNOWN);

        applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UNKNOWN);
        InstanceInfo.InstanceStatus existingStatus2 = applicationInfoManager.getInfo().getStatus();
        assertEquals(existingStatus2, existingStatus1);
    }
}

package com.netflix.appinfo;

import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.appinfo.AmazonInfo.MetaDataKey.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class ApplicationInfoManagerTest {

    private EurekaInstanceConfig config = spy(new CloudInstanceConfig());
    private String dummyDefault = "dummyDefault";
    private InstanceInfo instanceInfo;
    private ApplicationInfoManager applicationInfoManager;

    @Before
    public void setUp() {
        instanceInfo = InstanceInfoGenerator.takeOne();
        this.applicationInfoManager = new ApplicationInfoManager(config, instanceInfo);
        when(config.getDefaultAddressResolutionOrder()).thenReturn(new String[] {
                publicHostname.name(),
                localIpv4.name()
        });
        when(config.getHostName(anyBoolean())).thenReturn(dummyDefault);
    }

    @Test
    public void testRefreshDataCenterInfoWithAmazonInfo() {
        AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
        String newPublicHostname = "newValue";
        assertThat(instanceInfo.getDefaultAddress(), is(not(newPublicHostname)));

        info.getMetadata().put(publicHostname.getName(), newPublicHostname);
        applicationInfoManager.refreshDataCenterInfoIfRequired();

        assertThat(instanceInfo.getDefaultAddress(), is(newPublicHostname));
    }

    @Test
    public void testRefreshDataCenterInfoWithMyDataCenterInfo() {
        // override datacenterinfo to the non-aws version
        MyDataCenterInfo myDataCenterInfo = new MyDataCenterInfo(DataCenterInfo.Name.MyOwn);
        instanceInfo = new InstanceInfo.Builder(instanceInfo).setDataCenterInfo(myDataCenterInfo).build();

        DataCenterInfo info = instanceInfo.getDataCenterInfo();
        assertThat(info, instanceOf(MyDataCenterInfo.class));

        applicationInfoManager.refreshDataCenterInfoIfRequired();
        assertThat(instanceInfo.getDefaultAddress(), is(dummyDefault));
    }
}

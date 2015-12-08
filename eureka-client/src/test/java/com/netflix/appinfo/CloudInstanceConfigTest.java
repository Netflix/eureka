package com.netflix.appinfo;

import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.netflix.appinfo.AmazonInfo.MetaDataKey.localIpv4;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.publicHostname;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author David Liu
 */
public class CloudInstanceConfigTest {

    private CloudInstanceConfig config;
    private String dummyDefault = "dummyDefault";
    private InstanceInfo instanceInfo;

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("eureka.validateInstanceId", "false");
    }

    @AfterClass
    public static void tearDownClass() {
        System.clearProperty("eureka.validateInstanceId");
    }

    @Before
    public void setUp() {
        config = spy(new CloudInstanceConfig());

        instanceInfo = InstanceInfoGenerator.takeOne();
        when(config.getDefaultAddressResolutionOrder()).thenReturn(new String[] {
                publicHostname.name(),
                localIpv4.name()
        });
        when(config.getHostName(anyBoolean())).thenReturn(dummyDefault);
    }

    @Test
    public void testResolveDefaultAddress() {
        AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
        assertThat(config.resolveDefaultAddress(info), is(info.get(publicHostname)));

        info.getMetadata().remove(publicHostname.getName());
        assertThat(config.resolveDefaultAddress(info), is(info.get(localIpv4)));

        info.getMetadata().remove(localIpv4.getName());
        assertThat(config.resolveDefaultAddress(info), is(dummyDefault));
    }
}

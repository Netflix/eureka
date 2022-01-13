package com.netflix.appinfo;

import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.appinfo.AmazonInfo.MetaDataKey.ipv6;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.localIpv4;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.publicHostname;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;


/**
 * @author David Liu
 */
public class CloudInstanceConfigTest {

    private CloudInstanceConfig config;
    private String dummyDefault = "dummyDefault";
    private InstanceInfo instanceInfo;

    @Before
    public void setUp() {
        instanceInfo = InstanceInfoGenerator.takeOne();
    }

    @Test
    public void testResolveDefaultAddress() {
        AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
        config = createConfig(info);
        assertThat(config.resolveDefaultAddress(false), is(info.get(publicHostname)));

        info.getMetadata().remove(publicHostname.getName());
        config = createConfig(info);
        assertThat(config.resolveDefaultAddress(false), is(info.get(localIpv4)));

        info.getMetadata().remove(localIpv4.getName());
        config = createConfig(info);
        assertThat(config.resolveDefaultAddress(false), is(info.get(ipv6)));

        info.getMetadata().remove(ipv6.getName());
        config = createConfig(info);
        assertThat(config.resolveDefaultAddress(false), is(dummyDefault));
    }

    @Test
    public void testBroadcastPublicIpv4Address() {
        AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();

        config = createConfig(info);

        // this should work because the test utils class sets the ipAddr to the public IP of the instance
        assertEquals(instanceInfo.getIPAddr(), config.getIpAddress());
    }

    private CloudInstanceConfig createConfig(AmazonInfo info) {

        return new CloudInstanceConfig(info) {
            @Override
            public String[] getDefaultAddressResolutionOrder() {
                return new String[] {
                        publicHostname.name(),
                        localIpv4.name(),
                        ipv6.name()
                };
            }

            @Override
            public String getHostName(boolean refresh) {
                return dummyDefault;
            }

            @Override
            public boolean shouldBroadcastPublicIpv4Addr() { return true; }
        };
    }
}

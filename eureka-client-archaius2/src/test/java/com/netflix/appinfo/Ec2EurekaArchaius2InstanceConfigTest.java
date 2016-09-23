package com.netflix.appinfo;

import com.netflix.archaius.config.MapConfig;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static com.netflix.appinfo.AmazonInfo.MetaDataKey.localIpv4;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.publicHostname;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author David Liu
 */
public class Ec2EurekaArchaius2InstanceConfigTest {
    private Ec2EurekaArchaius2InstanceConfig config;
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
        assertThat(config.resolveDefaultAddress(false), is(dummyDefault));
    }

    private Ec2EurekaArchaius2InstanceConfig createConfig(AmazonInfo info) {

        return new Ec2EurekaArchaius2InstanceConfig(MapConfig.from(Collections.<String, String>emptyMap()), info) {
            @Override
            public String[] getDefaultAddressResolutionOrder() {
                return new String[] {
                        publicHostname.name(),
                        localIpv4.name()
                };
            }

            @Override
            public String getHostName(boolean refresh) {
                return dummyDefault;
            }
        };
    }
}


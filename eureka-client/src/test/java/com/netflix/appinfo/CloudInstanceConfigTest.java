package com.netflix.appinfo;

import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.netflix.appinfo.AmazonInfo.MetaDataKey.amiId;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.instanceId;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.localIpv4;
import static com.netflix.appinfo.AmazonInfo.MetaDataKey.publicHostname;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

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
        instanceInfo = InstanceInfoGenerator.takeOne();

        config = new CloudInstanceConfig((AmazonInfo) instanceInfo.getDataCenterInfo()) {
            @Override
            public synchronized void refreshAmazonInfo() {
                // Do nothing
            }

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

    @Test
    public void testResolveDefaultAddress() {
        config.info = (AmazonInfo) instanceInfo.getDataCenterInfo();
        AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
        assertThat(config.resolveDefaultAddress(false), is(info.get(publicHostname)));

        config.info.getMetadata().remove(publicHostname.getName());
        assertThat(config.resolveDefaultAddress(false), is(info.get(localIpv4)));

        config.info.getMetadata().remove(localIpv4.getName());
        assertThat(config.resolveDefaultAddress(false), is(dummyDefault));
    }

    @Test
    public void testAmazonInfoNoUpdateIfEqual() {
        AmazonInfo oldInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();

        AmazonInfo newInfo = copyAmazonInfo(instanceInfo);
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(false));
    }

    @Test
    public void testAmazonInfoNoUpdateIfEmpty() {
        AmazonInfo oldInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();

        AmazonInfo newInfo = new AmazonInfo();
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(false));
    }

    @Test
    public void testAmazonInfoNoUpdateIfNoInstanceId() {
        AmazonInfo oldInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();

        AmazonInfo newInfo = copyAmazonInfo(instanceInfo);
        newInfo.getMetadata().remove(instanceId.getName());
        assertThat(newInfo.getId(), is(nullValue()));
        assertThat(newInfo.get(instanceId), is(nullValue()));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(false));

        newInfo.getMetadata().put(instanceId.getName(), "");
        assertThat(newInfo.getId(), is(""));
        assertThat(newInfo.get(instanceId), is(""));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(false));
    }

    @Test
    public void testAmazonInfoNoUpdateIfNoLocalIpv4() {
        AmazonInfo oldInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();

        AmazonInfo newInfo = copyAmazonInfo(instanceInfo);
        newInfo.getMetadata().remove(localIpv4.getName());
        assertThat(newInfo.get(localIpv4), is(nullValue()));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(false));

        newInfo.getMetadata().put(localIpv4.getName(), "");
        assertThat(newInfo.get(localIpv4), is(""));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(false));
    }

    @Test
    public void testAmazonInfoUpdatePositiveCase() {
        AmazonInfo oldInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();

        AmazonInfo newInfo = copyAmazonInfo(instanceInfo);
        newInfo.getMetadata().remove(amiId.getName());
        assertThat(newInfo.getMetadata().size(), is(oldInfo.getMetadata().size() - 1));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(true));

        String newKey = "someNewKey";
        newInfo.getMetadata().put(newKey, "bar");
        assertThat(newInfo.getMetadata().size(), is(oldInfo.getMetadata().size()));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo, oldInfo), is(true));
    }


    private static AmazonInfo copyAmazonInfo(InstanceInfo instanceInfo) {
        AmazonInfo currInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();
        AmazonInfo copyInfo = new AmazonInfo();
        for (String key : currInfo.getMetadata().keySet()) {
            copyInfo.getMetadata().put(key, currInfo.getMetadata().get(key));
        }
        return copyInfo;
    }
}

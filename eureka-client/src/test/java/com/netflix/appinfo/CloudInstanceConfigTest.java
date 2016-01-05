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
        assertThat(config.resolveDefaultAddress(), is(info.get(publicHostname)));

        config.info.getMetadata().remove(publicHostname.getName());
        assertThat(config.resolveDefaultAddress(), is(info.get(localIpv4)));

        config.info.getMetadata().remove(localIpv4.getName());
        assertThat(config.resolveDefaultAddress(), is(dummyDefault));
    }

    @Test
    public void testAmazonInfoUpdate() {
        AmazonInfo oldInfo = (AmazonInfo) instanceInfo.getDataCenterInfo();

        // false for update if equal
        AmazonInfo newInfo1 = oldInfo;
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo1, oldInfo), is(false));

        // false for update if new is empty
        AmazonInfo newInfo2 = new AmazonInfo();
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo2, oldInfo), is(false));

        AmazonInfo newInfo3 = new AmazonInfo();
        for (String key : oldInfo.getMetadata().keySet()) {
            newInfo3.getMetadata().put(key, oldInfo.getMetadata().get(key));
        }
        int originalInfo3Size = newInfo3.getMetadata().size();

        // true for update if new contains diff data
        newInfo3.getMetadata().put(AmazonInfo.MetaDataKey.instanceId.getName(), "foo");
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo3, oldInfo), is(true));

        // true for update if new contains more data
        String newKey = "someNewKey";
        newInfo3.getMetadata().put(newKey, "bar");
        assertThat(newInfo3.getMetadata().size(), is(originalInfo3Size + 1));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo3, oldInfo), is(true));

        // false if there is now less data
        newInfo3.getMetadata().remove(newKey);
        newInfo3.getMetadata().remove(AmazonInfo.MetaDataKey.instanceId.getName());
        assertThat(newInfo3.getMetadata().size(), is(originalInfo3Size - 1));
        assertThat(CloudInstanceConfig.shouldUpdate(newInfo3, oldInfo), is(false));
    }
}

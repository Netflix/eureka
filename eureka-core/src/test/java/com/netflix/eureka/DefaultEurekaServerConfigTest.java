package com.netflix.eureka;

import java.util.Map;
import java.util.Set;

import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class DefaultEurekaServerConfigTest {

    @Test
    public void testRemoteRegionUrlsWithName2Regions() throws Exception {
        String region1 = "myregion1";
        String region1url = "http://local:888/eee";
        String region2 = "myregion2";
        String region2url = "http://local:888/eee";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegionUrlsWithName", region1
                + ';' + region1url
                + ',' + region2
                + ';' + region2url);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Map<String, String> remoteRegionUrlsWithName = config.getRemoteRegionUrlsWithName();

        Assert.assertEquals("Unexpected remote region url count.", 2, remoteRegionUrlsWithName.size());
        Assert.assertTrue("Remote region 1 not found.", remoteRegionUrlsWithName.containsKey(region1));
        Assert.assertTrue("Remote region 2 not found.", remoteRegionUrlsWithName.containsKey(region2));
        Assert.assertEquals("Unexpected remote region 1 url.", region1url, remoteRegionUrlsWithName.get(region1));
        Assert.assertEquals("Unexpected remote region 2 url.", region2url, remoteRegionUrlsWithName.get(region2));

    }

    @Test
    public void testRemoteRegionUrlsWithName1Region() throws Exception {
        String region1 = "myregion1";
        String region1url = "http://local:888/eee";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegionUrlsWithName", region1
                + ';' + region1url);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Map<String, String> remoteRegionUrlsWithName = config.getRemoteRegionUrlsWithName();

        Assert.assertEquals("Unexpected remote region url count.", 1, remoteRegionUrlsWithName.size());
        Assert.assertTrue("Remote region 1 not found.", remoteRegionUrlsWithName.containsKey(region1));
        Assert.assertEquals("Unexpected remote region 1 url.", region1url, remoteRegionUrlsWithName.get(region1));

    }

    @Test
    public void testGetGlobalAppWhiteList() throws Exception {
        String whitelistApp = "myapp";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.global.appWhiteList", whitelistApp);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Set<String> globalList = config.getRemoteRegionAppWhitelist(null);
        Assert.assertNotNull("Global whitelist is null.", globalList);
        Assert.assertEquals("Global whitelist not as expected.", 1, globalList.size());
        Assert.assertEquals("Global whitelist not as expected.", whitelistApp, globalList.iterator().next());
    }

    @Test
    public void testGetRegionAppWhiteList() throws Exception {
        String globalWhiteListApp = "myapp";
        String regionWhiteListApp = "myapp";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.global.appWhiteList", globalWhiteListApp);
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.region1.appWhiteList", regionWhiteListApp);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Set<String> regionList = config.getRemoteRegionAppWhitelist(null);
        Assert.assertNotNull("Region whitelist is null.", regionList);
        Assert.assertEquals("Region whitelist not as expected.", 1, regionList.size());
        Assert.assertEquals("Region whitelist not as expected.", regionWhiteListApp, regionList.iterator().next());
    }
}

package com.netflix.eureka;

import java.util.Map;
import java.util.Set;

import com.netflix.config.ConfigurationManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

        Assertions.assertEquals(2, remoteRegionUrlsWithName.size(), "Unexpected remote region url count.");
        Assertions.assertTrue(remoteRegionUrlsWithName.containsKey(region1), "Remote region 1 not found.");
        Assertions.assertTrue(remoteRegionUrlsWithName.containsKey(region2), "Remote region 2 not found.");
        Assertions.assertEquals(region1url, remoteRegionUrlsWithName.get(region1), "Unexpected remote region 1 url.");
        Assertions.assertEquals(region2url, remoteRegionUrlsWithName.get(region2), "Unexpected remote region 2 url.");

    }

    @Test
    public void testRemoteRegionUrlsWithName1Region() throws Exception {
        String region1 = "myregion1";
        String region1url = "http://local:888/eee";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegionUrlsWithName", region1
                + ';' + region1url);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Map<String, String> remoteRegionUrlsWithName = config.getRemoteRegionUrlsWithName();

        Assertions.assertEquals(1, remoteRegionUrlsWithName.size(), "Unexpected remote region url count.");
        Assertions.assertTrue(remoteRegionUrlsWithName.containsKey(region1), "Remote region 1 not found.");
        Assertions.assertEquals(region1url, remoteRegionUrlsWithName.get(region1), "Unexpected remote region 1 url.");

    }

    @Test
    public void testGetGlobalAppWhiteList() throws Exception {
        String whitelistApp = "myapp";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.global.appWhiteList", whitelistApp);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Set<String> globalList = config.getRemoteRegionAppWhitelist(null);
        Assertions.assertNotNull(globalList, "Global whitelist is null.");
        Assertions.assertEquals(1, globalList.size(), "Global whitelist not as expected.");
        Assertions.assertEquals(whitelistApp, globalList.iterator().next(), "Global whitelist not as expected.");
    }

    @Test
    public void testGetRegionAppWhiteList() throws Exception {
        String globalWhiteListApp = "myapp";
        String regionWhiteListApp = "myapp";
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.global.appWhiteList", globalWhiteListApp);
        ConfigurationManager.getConfigInstance().setProperty("eureka.remoteRegion.region1.appWhiteList", regionWhiteListApp);
        DefaultEurekaServerConfig config = new DefaultEurekaServerConfig();
        Set<String> regionList = config.getRemoteRegionAppWhitelist(null);
        Assertions.assertNotNull(regionList, "Region whitelist is null.");
        Assertions.assertEquals(1, regionList.size(), "Region whitelist not as expected.");
        Assertions.assertEquals(regionWhiteListApp, regionList.iterator().next(), "Region whitelist not as expected.");
    }
}

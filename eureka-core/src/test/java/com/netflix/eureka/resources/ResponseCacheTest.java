package com.netflix.eureka.resources;

import com.netflix.blitz4j.LoggingConfiguration;
import com.netflix.discovery.AbstractDiscoveryClientTester;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.Version;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class ResponseCacheTest extends AbstractDiscoveryClientTester {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        EurekaServerConfigurationManager.getInstance().setConfiguration(new DefaultEurekaServerConfig());
        LoggingConfiguration.getInstance().configure();
        PeerAwareInstanceRegistry.getInstance().syncUp();
    }

    @Test
    public void testInvalidate() throws Exception {
        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ResponseCache cache = ResponseCache.getInstance();
        ResponseCache.Key key = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, LOCAL_REGION_APP_NAME,
                                                      ResponseCache.KeyType.JSON, Version.V1);
        String response = cache.get(key);
        Assert.assertNotNull("Cache get returned null.", response);

        PeerAwareInstanceRegistry.getInstance().cancel(LOCAL_REGION_APP_NAME, LOCAL_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key));
    }

    @Test
    public void testInvalidateWithRemoteRegion() throws Exception {
        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ResponseCache cache = ResponseCache.getInstance();
        ResponseCache.Key key = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, LOCAL_REGION_APP_NAME,
                                                      new String[] {REMOTE_REGION},
                                                      ResponseCache.KeyType.JSON, Version.V1);

        Assert.assertNotNull("Cache get returned null.", cache.get(key));

        PeerAwareInstanceRegistry.getInstance().cancel(LOCAL_REGION_APP_NAME, LOCAL_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key));
    }

    @Test
    public void testInvalidateWithMultipleRemoteRegions() throws Exception {
        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ResponseCache cache = ResponseCache.getInstance();
        ResponseCache.Key key1 = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, LOCAL_REGION_APP_NAME,
                                                      new String[] {REMOTE_REGION, "myregion2"},
                                                      ResponseCache.KeyType.JSON, Version.V1);
        ResponseCache.Key key2 = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, LOCAL_REGION_APP_NAME,
                                                      new String[] {REMOTE_REGION},
                                                      ResponseCache.KeyType.JSON, Version.V1);

        Assert.assertNotNull("Cache get returned null.", cache.get(key1));
        Assert.assertNotNull("Cache get returned null.", cache.get(key2));

        PeerAwareInstanceRegistry.getInstance().cancel(LOCAL_REGION_APP_NAME, LOCAL_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key1));
        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key2));
    }
}

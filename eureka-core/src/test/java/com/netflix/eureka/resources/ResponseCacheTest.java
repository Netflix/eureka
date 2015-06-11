package com.netflix.eureka.resources;

import com.netflix.blitz4j.LoggingConfiguration;
import com.netflix.eureka.AbstractTester;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.PeerAwareInstanceRegistryImpl;
import com.netflix.eureka.Version;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class ResponseCacheTest extends AbstractTester {

    private static final String REMOTE_REGION = "myremote";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        EurekaServerConfigurationManager.getInstance().setConfiguration(new DefaultEurekaServerConfig());
        LoggingConfiguration.getInstance().configure();
        PeerAwareInstanceRegistryImpl.getInstance().syncUp();
    }

    @Test
    public void testInvalidate() throws Exception {
        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ResponseCache cache = ResponseCache.getInstance();
        ResponseCache.Key key = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, REMOTE_REGION_APP_NAME,
                ResponseCache.KeyType.JSON, Version.V1);
        String response = cache.get(key, false);
        Assert.assertNotNull("Cache get returned null.", response);

        PeerAwareInstanceRegistryImpl.getInstance().cancel(REMOTE_REGION_APP_NAME, REMOTE_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key, true));
    }

    @Test
    public void testInvalidateWithRemoteRegion() throws Exception {
        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ResponseCache cache = ResponseCache.getInstance();
        ResponseCache.Key key = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, REMOTE_REGION_APP_NAME,
                new String[]{REMOTE_REGION},
                ResponseCache.KeyType.JSON, Version.V1);

        Assert.assertNotNull("Cache get returned null.", cache.get(key, false));

        PeerAwareInstanceRegistryImpl.getInstance().cancel(REMOTE_REGION_APP_NAME, REMOTE_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key, true));
    }

    @Test
    public void testInvalidateWithMultipleRemoteRegions() throws Exception {
        EurekaServerConfig serverConfig = new DefaultEurekaServerConfig();
        EurekaServerConfigurationManager.getInstance().setConfiguration(serverConfig);
        ResponseCache cache = ResponseCache.getInstance();
        ResponseCache.Key key1 = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, REMOTE_REGION_APP_NAME,
                new String[]{REMOTE_REGION, "myregion2"},
                ResponseCache.KeyType.JSON, Version.V1);
        ResponseCache.Key key2 = new ResponseCache.Key(ResponseCache.Key.EntityType.Application, REMOTE_REGION_APP_NAME,
                new String[]{REMOTE_REGION},
                ResponseCache.KeyType.JSON, Version.V1);

        Assert.assertNotNull("Cache get returned null.", cache.get(key1, false));
        Assert.assertNotNull("Cache get returned null.", cache.get(key2, false));

        PeerAwareInstanceRegistryImpl.getInstance().cancel(REMOTE_REGION_APP_NAME, REMOTE_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key1, true));
        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key2, true));
    }
}

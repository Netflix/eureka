package com.netflix.eureka.registry;

import com.netflix.appinfo.EurekaAccept;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.eureka.AbstractTester;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.Version;
import com.netflix.eureka.resources.DefaultServerCodecs;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * @author Nitesh Kant
 */
public class ResponseCacheTest extends AbstractTester {

    private static final String REMOTE_REGION = "myremote";

    private PeerAwareInstanceRegistry testRegistry;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // create a new registry that is sync'ed up with the default registry in the AbstractTester,
        // but disable transparent fetch to the remote for gets
        EurekaServerConfig serverConfig = spy(new DefaultEurekaServerConfig());
        doReturn(true).when(serverConfig).disableTransparentFallbackToOtherRegion();

        testRegistry = new PeerAwareInstanceRegistryImpl(
                serverConfig,
                new DefaultEurekaClientConfig(),
                new DefaultServerCodecs(serverConfig),
                client
        );
        testRegistry.init(serverContext.getPeerEurekaNodes());
        testRegistry.syncUp();
    }

    @Test
    public void testInvalidate() throws Exception {
        ResponseCacheImpl cache = (ResponseCacheImpl) testRegistry.getResponseCache();
        Key key = new Key(Key.EntityType.Application, REMOTE_REGION_APP_NAME,
                Key.KeyType.JSON, Version.V1, EurekaAccept.full);
        String response = cache.get(key, false);
        Assert.assertNotNull("Cache get returned null.", response);

        testRegistry.cancel(REMOTE_REGION_APP_NAME, REMOTE_REGION_INSTANCE_1_HOSTNAME, true);
        Assert.assertNull("Cache after invalidate did not return null for write view.", cache.get(key, true));
    }

    @Test
    public void testInvalidateWithRemoteRegion() throws Exception {
        ResponseCacheImpl cache = (ResponseCacheImpl) testRegistry.getResponseCache();
        Key key = new Key(
                Key.EntityType.Application,
                REMOTE_REGION_APP_NAME,
                Key.KeyType.JSON, Version.V1, EurekaAccept.full, new String[]{REMOTE_REGION}
        );

        Assert.assertNotNull("Cache get returned null.", cache.get(key, false));

        testRegistry.cancel(REMOTE_REGION_APP_NAME, REMOTE_REGION_INSTANCE_1_HOSTNAME, true);
        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key));
    }

    @Test
    public void testInvalidateWithMultipleRemoteRegions() throws Exception {
        ResponseCacheImpl cache = (ResponseCacheImpl) testRegistry.getResponseCache();
        Key key1 = new Key(
                Key.EntityType.Application,
                REMOTE_REGION_APP_NAME,
                Key.KeyType.JSON, Version.V1, EurekaAccept.full, new String[]{REMOTE_REGION, "myregion2"}
        );
        Key key2 = new Key(
                Key.EntityType.Application,
                REMOTE_REGION_APP_NAME,
                Key.KeyType.JSON, Version.V1, EurekaAccept.full, new String[]{REMOTE_REGION}
        );

        Assert.assertNotNull("Cache get returned null.", cache.get(key1, false));
        Assert.assertNotNull("Cache get returned null.", cache.get(key2, false));

        testRegistry.cancel(REMOTE_REGION_APP_NAME, REMOTE_REGION_INSTANCE_1_HOSTNAME, true);

        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key1, true));
        Assert.assertNull("Cache after invalidate did not return null.", cache.get(key2, true));
    }
}

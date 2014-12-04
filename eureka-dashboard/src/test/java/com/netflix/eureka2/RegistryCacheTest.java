package com.netflix.eureka2;

import com.netflix.eureka2.registry.InstanceInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Map;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RegistryCacheTest extends RegistryTestBase {

    @Test
    public void checkRegistryCount() {
        RegistryCache registryCache = new RegistryCache(dashboardEurekaClientBuilder);
        final Map<String, InstanceInfo> regCache = registryCache.getCache();
        assertTrue(regCache != null);
        assertTrue(regCache.size() == 4);
    }

}

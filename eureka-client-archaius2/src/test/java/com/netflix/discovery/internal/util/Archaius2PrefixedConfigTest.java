package com.netflix.discovery.internal.util;

import com.netflix.archaius.api.Config;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author David Liu
 */
public class Archaius2PrefixedConfigTest {

    @Test
    public void testPrefixes() {
        Config configInstance = Mockito.mock(Config.class);

        Archaius2PrefixedConfig config = new Archaius2PrefixedConfig(configInstance);
        Assert.assertEquals("", config.getNamespace());

        config = new Archaius2PrefixedConfig(configInstance, "foo");
        Assert.assertEquals("foo.", config.getNamespace());

        config = new Archaius2PrefixedConfig(configInstance, "foo", "bar");
        Assert.assertEquals("foo.bar.", config.getNamespace());
    }
}

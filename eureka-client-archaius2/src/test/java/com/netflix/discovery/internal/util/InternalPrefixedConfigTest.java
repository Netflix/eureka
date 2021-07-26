package com.netflix.discovery.internal.util;

import com.netflix.archaius.api.Config;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * @author David Liu
 */
public class InternalPrefixedConfigTest {

    @Test
    public void testPrefixes() {
        Config configInstance = Mockito.mock(Config.class);

        InternalPrefixedConfig config = new InternalPrefixedConfig(configInstance);
        Assertions.assertEquals("", config.getNamespace());

        config = new InternalPrefixedConfig(configInstance, "foo");
        Assertions.assertEquals("foo.", config.getNamespace());

        config = new InternalPrefixedConfig(configInstance, "foo", "bar");
        Assertions.assertEquals("foo.bar.", config.getNamespace());
    }
}

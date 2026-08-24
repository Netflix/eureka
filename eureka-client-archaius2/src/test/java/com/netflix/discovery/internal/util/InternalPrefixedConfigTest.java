package com.netflix.discovery.internal.util;

import com.netflix.archaius.api.Config;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author David Liu
 */
public class InternalPrefixedConfigTest {

    @Test
    public void testPrefixes() {
        Config configInstance = Mockito.mock(Config.class);

        InternalPrefixedConfig config = new InternalPrefixedConfig(configInstance);
        Assert.assertEquals("", config.getNamespace());

        config = new InternalPrefixedConfig(configInstance, "foo");
        Assert.assertEquals("foo.", config.getNamespace());

        config = new InternalPrefixedConfig(configInstance, "foo", "bar");
        Assert.assertEquals("foo.bar.", config.getNamespace());
    }

    @Test
    public void testEmptyAndNullPrefixesAreIgnored() {
        Config configInstance = Mockito.mock(Config.class);

        InternalPrefixedConfig config = new InternalPrefixedConfig(configInstance, null, "", "foo");

        Assert.assertEquals("foo.", config.getNamespace());
    }

    @Test
    public void testPrefixEndingWithDotIsNotDuplicated() {
        Config configInstance = Mockito.mock(Config.class);

        InternalPrefixedConfig config = new InternalPrefixedConfig(configInstance, "foo.", "bar");

        Assert.assertEquals("foo.bar.", config.getNamespace());
    }
}

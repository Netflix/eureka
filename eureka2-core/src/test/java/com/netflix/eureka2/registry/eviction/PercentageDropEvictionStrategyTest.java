package com.netflix.eureka2.registry.eviction;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author David Liu
 */
public class PercentageDropEvictionStrategyTest {

    @Test
    public void testAllowedToEvict() {
        int expectedSize = 100;
        int allowedPercentage = 50;

        PercentageDropEvictionStrategy strategy = new PercentageDropEvictionStrategy(allowedPercentage);

        int actualSize = (expectedSize * allowedPercentage / 100) + 1;  // just above threshold
        int result = strategy.allowedToEvict(expectedSize, actualSize);
        Assert.assertTrue(result > 0);

        actualSize = (expectedSize * allowedPercentage / 100) - 1;  // just below threshold
        result = strategy.allowedToEvict(expectedSize, actualSize);
        Assert.assertTrue(result < 0);

        actualSize = (expectedSize * allowedPercentage / 100);  // exactly equal
        result = strategy.allowedToEvict(expectedSize, actualSize);
        Assert.assertTrue(result == 0);

    }
}

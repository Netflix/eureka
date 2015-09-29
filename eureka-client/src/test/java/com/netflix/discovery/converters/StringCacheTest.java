package com.netflix.discovery.converters;

import com.netflix.discovery.util.StringCache;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author Tomasz Bak
 */
public class StringCacheTest {

    public static final int CACHE_SIZE = 100000;

    @Test
    public void testVerifyStringsAreGarbageCollectedIfNotReferenced() throws Exception {
        StringCache cache = new StringCache();
        for (int i = 0; i < CACHE_SIZE; i++) {
            cache.cachedValueOf("id#" + i);
        }
        gc();
        // Testing GC behavior is unpredictable, so we set here low target level
        // The tests run on desktop show that all strings are removed actually.
        assertTrue(cache.size() < CACHE_SIZE * 0.1);
    }

    public static void gc() {
        System.gc();
        System.runFinalization();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // IGNORE
        }
    }
}
package com.netflix.eureka2.data.toplogy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public class DependencyProfileTest {
    @Test
    public void testStreamFromGeneratesItemAccordingToTheirWeights() throws Exception {
        DependencyProfile rare = new DependencyProfile(new HashMap<ApplicationProfile, Integer>(), 1);
        DependencyProfile frequent = new DependencyProfile(new HashMap<ApplicationProfile, Integer>(), 2);

        Iterator<DependencyProfile> iterator = DependencyProfile.streamFrom(Arrays.asList(rare, frequent));
        int rareCounter = 0;
        int frequentCounter = 0;
        for (int i = 0; i < 30; i++) {
            DependencyProfile next = iterator.next();
            if (next == rare) {
                rareCounter++;
            } else if (next == frequent) {
                frequentCounter++;
            } else {
                fail("Got " + next);
            }
        }
        assertThat(frequentCounter > rareCounter, is(true));
    }
}
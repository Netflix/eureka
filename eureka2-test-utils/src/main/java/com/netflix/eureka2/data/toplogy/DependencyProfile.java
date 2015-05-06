package com.netflix.eureka2.data.toplogy;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Describes an interest of a particular subscriber in a given set of application profiles.
 * Each application profile is associated with a number of subscriptions to different application
 * instances derived from this profile.
 *
 * @author Tomasz Bak
 */
public class DependencyProfile {
    private final Map<ApplicationProfile, Integer> subscriptionsPerProfile;
    private final int quantity;

    public DependencyProfile(Map<ApplicationProfile, Integer> subscriptionsPerProfile, int quantity) {
        this.subscriptionsPerProfile = subscriptionsPerProfile;
        this.quantity = quantity;
    }

    public Map<ApplicationProfile, Integer> getSubscriptionsPerProfile() {
        return subscriptionsPerProfile;
    }

    public int getQuantity() {
        return quantity;
    }

    /**
     * Infinite stream of {@link DependencyProfile} objects, taken randomly from the provided
     * source. As each {@link DependencyProfile} has its own weight (based on quantity attribute),
     * its frequency in the stream depends on the weight value.
     */
    public static Iterator<DependencyProfile> streamFrom(final List<DependencyProfile> source) {
        int totalSize = 0;
        for (DependencyProfile profile : source) {
            totalSize += profile.getQuantity();
        }
        final int[] mapper = new int[totalSize];
        int cpos = 0;
        for (int i = 0; i < source.size(); i++) {
            DependencyProfile profile = source.get(i);
            Arrays.fill(mapper, cpos, cpos + profile.getQuantity(), i);
        }

        return new Iterator<DependencyProfile>() {

            private final Random random = new Random();

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public DependencyProfile next() {
                int index = random.nextInt(mapper.length);
                return source.get(mapper[index]);
            }

            @Override
            public void remove() {
            }
        };
    }

    public static class DependencyProfileBuilder {
        private final Map<ApplicationProfile, Integer> subscriptionsPerProfile = new HashMap<>();
        private int quantity;

        public DependencyProfileBuilder withApplicationProfile(ApplicationProfile applicationProfile, int count) {
            subscriptionsPerProfile.put(applicationProfile, count);
            return this;
        }

        public DependencyProfileBuilder withQuantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public DependencyProfile build() {
            return new DependencyProfile(subscriptionsPerProfile, quantity);
        }
    }
}

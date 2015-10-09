/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.util.SystemUtil;

/**
 * @author Tomasz Bak
 */
public final class ResolverUtils {

    private static final String LOCAL_IPV4_ADDRESS = SystemUtil.getServerIPv4();

    private static final Pattern ZONE_RE = Pattern.compile("(txt\\.)?([^.]+).*");

    private ResolverUtils() {
    }

    /**
     * @return returns two element array with first item containing list of endpoints from client's zone,
     *         and in the second list all the remaining ones
     */
    public static List<AwsEndpoint>[] splitByZone(List<AwsEndpoint> eurekaEndpoints, String myZone) {
        if (eurekaEndpoints.isEmpty()) {
            return new List[]{Collections.emptyList(), Collections.emptyList()};
        }
        if (myZone == null) {
            return new List[]{Collections.emptyList(), new ArrayList<>(eurekaEndpoints)};
        }
        List<AwsEndpoint> myZoneList = new ArrayList<>(eurekaEndpoints.size());
        List<AwsEndpoint> remainingZonesList = new ArrayList<>(eurekaEndpoints.size());

        for (AwsEndpoint endpoint : eurekaEndpoints) {
            if (myZone.equalsIgnoreCase(endpoint.getZone())) {
                myZoneList.add(endpoint);
            } else {
                remainingZonesList.add(endpoint);
            }
        }
        return new List[]{myZoneList, remainingZonesList};
    }

    public static String extractZoneFromHostName(String hostName) {
        Matcher matcher = ZONE_RE.matcher(hostName);
        if (matcher.matches()) {
            return matcher.group(2);
        }
        return null;
    }

    /**
     * Randomize server list using local IPv4 address hash as a seed.
     *
     * @return a copy of the original list with elements in the random order
     */
    public static <T extends EurekaEndpoint> List<T> randomize(List<T> list) {
        List<T> randomList = new ArrayList<>(list);
        if (randomList.size() < 2) {
            return randomList;
        }
        Random random = new Random(LOCAL_IPV4_ADDRESS.hashCode());
        int last = randomList.size() - 1;
        for (int i = 0; i < last; i++) {
            int pos = random.nextInt(randomList.size() - i);
            if (pos != i) {
                Collections.swap(randomList, i, pos);
            }
        }
        return randomList;
    }

    /**
     * @return true if both list are the same, possibly in a different order
     */
    public static <T extends EurekaEndpoint> boolean identical(List<T> firstList, List<T> secondList) {
        if (firstList.size() != secondList.size()) {
            return false;
        }
        HashSet<T> compareSet = new HashSet<>(firstList);
        compareSet.removeAll(secondList);
        return compareSet.isEmpty();
    }
}

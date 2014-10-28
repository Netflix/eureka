/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.rx.eureka.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.netflix.rx.eureka.registry.NetworkAddress.ProtocolType;

import static java.util.Arrays.*;

/**
 * TODO: we need a flexible way to choose the best address given certain conditions
 *
 * @author Tomasz Bak
 */
public final class NetworkAddresses {

    public enum Preference {
        Public,
        PublicOnly,
        Private,
        PrivateOnly,
        HostName,
        HostNameOnly,
        IpAddress,
        IpAddressOnly,
        Ipv4,
        Ipv4Only
    }

    private NetworkAddresses() {
    }

    public static String select(List<NetworkAddress> addresses, Preference... preferenceList) {
        EnumSet<Preference> preferences = EnumSet.copyOf(asList(preferenceList));

        List<NetworkAddress> filtered = filterOut(addresses, preferences);
        if (filtered.isEmpty()) {
            return null;
        }
        Collections.sort(filtered, new PreferenceComparator(preferences));
        return preferences.contains(Preference.HostName) && filtered.get(0).getHostName() != null ?
                filtered.get(0).getHostName() : filtered.get(0).getIpAddress();
    }

    public static List<NetworkAddress> filterOut(List<NetworkAddress> addresses, EnumSet<Preference> preferences) {
        List<NetworkAddress> matching = new ArrayList<NetworkAddress>();
        for (NetworkAddress address : addresses) {
            if (matches(address, preferences)) {
                matching.add(address);
            }
        }
        return matching;
    }

    public static boolean matches(NetworkAddress address, EnumSet<Preference> preferences) {
        return negativeScore(address, preferences) == 0;
    }

    private static int positiveScore(NetworkAddress address, EnumSet<Preference> preferences) {
        int score = 0;
        if (preferences.contains(Preference.Public) && address.isPublic()) {
            score++;
        }
        if (preferences.contains(Preference.Private) && !address.isPublic()) {
            score++;
        }
        if (preferences.contains(Preference.HostName) && address.getHostName() != null) {
            score++;
        }
        if (preferences.contains(Preference.IpAddress) && address.getIpAddress() != null) {
            score++;
        }
        if (preferences.contains(Preference.Ipv4) && address.getProtocolType() == ProtocolType.IPv4) {
            score++;
        }
        return score;
    }

    private static int negativeScore(NetworkAddress address, EnumSet<Preference> preferences) {
        int score = 0;
        if (preferences.contains(Preference.PublicOnly) && !address.isPublic()) {
            score++;
        }
        if (preferences.contains(Preference.PrivateOnly) && address.isPublic()) {
            score++;
        }
        if (preferences.contains(Preference.HostNameOnly) && address.getHostName() == null) {
            score++;
        }
        if (preferences.contains(Preference.IpAddressOnly) && address.getIpAddress() == null) {
            score++;
        }
        if (preferences.contains(Preference.Ipv4Only) && address.getProtocolType() != ProtocolType.IPv4) {
            score++;
        }
        return score;
    }

    static class PreferenceComparator implements Comparator<NetworkAddress> {
        private final EnumSet<Preference> preferences;

        PreferenceComparator(EnumSet<Preference> preferences) {
            this.preferences = preferences;
        }

        @Override
        public int compare(NetworkAddress o1, NetworkAddress o2) {
            int o1Negative = negativeScore(o1, preferences);
            int o2Negative = negativeScore(o2, preferences);
            if (o1Negative != o2Negative) {
                return o1Negative > o2Negative ? -1 : 1;
            }

            int o1Positive = positiveScore(o1, preferences);
            int o2Positive = positiveScore(o2, preferences);
            if (o1Positive == o2Positive) {
                return 0;
            }
            return o1Positive < o2Positive ? -1 : 1;
        }
    }
}

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

package com.netflix.eureka.utils;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Tomasz Bak
 */
public final class SystemUtil {

    public static final String IP4_LOOPBACK = "127.0.0.1";
    public static final String IP6_LOOPBACK = "0:0:0:0:0:0:0:1";

    private SystemUtil() {
    }

    public static String getHostName() {
        // Try first hostname system call, to avoid potential
        // issues with InetAddress.getLocalHost().getHostName()
        try {
            Process process = Runtime.getRuntime().exec("hostname");
            InputStream input = process.getInputStream();
            StringBuilder sb = new StringBuilder();
            byte[] bytes = new byte[256];
            int len;
            while ((len = input.read(bytes)) != -1) {
                sb.append(new String(bytes, 0, len, Charset.defaultCharset()));
            }
            if (process.exitValue() == 0) {
                return sb.toString().trim();
            }
        } catch (Exception ignore) {
            // Ignore this exception, we will try different way
        }

        // Try InetAddress.getLocalHost().getHostName()
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException("cannot resolve local host name", e);
        }
    }

    public static boolean isPublic(String ip) {
        return !isPrivate(ip) && !isLoopbackIP(ip);
    }

    public static boolean isPrivate(String ip) {
        return (ip.startsWith("10.") || ip.startsWith("172.16.") || ip.startsWith("192.168.") || ip.startsWith("fe80:")) && !isLoopbackIP(ip);
    }

    public static boolean isLoopbackIP(String ip) {
        return ip.equals(IP4_LOOPBACK) || ip.equals(IP6_LOOPBACK);
    }

    public static boolean isIPv6(String ip) {
        return ip.indexOf(':') != -1;
    }

    public static List<String> getLocalIPs() {
        ArrayList<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                Enumeration<InetAddress> inetAddresses = nic.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress address = inetAddresses.nextElement();
                    addresses.add(address.getHostAddress());
                }
            }
        } catch (SocketException e) {
            throw new RuntimeException("Cannot resolve local network addresses", e);
        }
        return addresses;
    }

    public static List<String> getPublicIPs() {
        ArrayList<String> publicIPs = new ArrayList<>();
        for (String ip : getLocalIPs()) {
            if (isPublic(ip)) {
                publicIPs.add(ip);
            }
        }
        return publicIPs;
    }

    public static List<String> getPrivateIPs() {
        ArrayList<String> privateIPs = new ArrayList<>();
        for (String ip : getLocalIPs()) {
            if (isPrivate(ip)) {
                privateIPs.add(ip);
            }
        }
        return privateIPs;
    }

    public static void main(String[] args) {
        System.out.println("Hostname: " + getHostName());
        System.out.println("Public IPs: " + getPublicIPs());
        System.out.println("Private IPs: " + getPrivateIPs());
    }
}

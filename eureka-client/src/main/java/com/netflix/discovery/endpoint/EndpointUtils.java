package com.netflix.discovery.endpoint;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This class contains some of the utility functions previously found in DiscoveryClient, but should be elsewhere.
 * It *does not yet* clean up the moved code.
 */
public class EndpointUtils {
    private static final Logger logger = LoggerFactory.getLogger(EndpointUtils.class);

    public static final String DEFAULT_REGION = "default";
    public static final String DEFAULT_ZONE = "default";

    public enum DiscoveryUrlType {
        CNAME, A
    }

    public static interface ServiceUrlRandomizer {
        void randomize(List<String> urlList);
    }

    public static class InstanceInfoBasedUrlRandomizer implements ServiceUrlRandomizer {
        private final InstanceInfo instanceInfo;

        public InstanceInfoBasedUrlRandomizer(InstanceInfo instanceInfo) {
            this.instanceInfo = instanceInfo;
        }

        @Override
        public void randomize(List<String> urlList) {
            int listSize = 0;
            if (urlList != null) {
                listSize = urlList.size();
            }
            if ((instanceInfo == null) || (listSize == 0)) {
                return;
            }
            // Find the hashcode of the instance hostname and use it to find an entry
            // and then arrange the rest of the entries after this entry.
            int instanceHashcode = instanceInfo.getHostName().hashCode();
            if (instanceHashcode < 0) {
                instanceHashcode = instanceHashcode * -1;
            }
            int backupInstance = instanceHashcode % listSize;
            for (int i = 0; i < backupInstance; i++) {
                String zone = urlList.remove(0);
                urlList.add(zone);
            }
        }
    }

    /**
     * Get the list of all eureka service urls for the eureka client to talk to.
     *
     * @param clientConfig the clientConfig to use
     * @param zone the zone in which the client resides
     * @param randomizer a randomizer to randomized returned urls, if loading from dns
     *
     * @return The list of all eureka service urls for the eureka client to talk to.
     */
    public static List<String> getDiscoveryServiceUrls(EurekaClientConfig clientConfig, String zone, ServiceUrlRandomizer randomizer) {
        boolean shouldUseDns = clientConfig.shouldUseDnsForFetchingServiceUrls();
        if (shouldUseDns) {
            return getServiceUrlsFromDNS(clientConfig, zone, clientConfig.shouldPreferSameZoneEureka(), randomizer);
        }
        return getServiceUrlsFromConfig(clientConfig, zone, clientConfig.shouldPreferSameZoneEureka());
    }

    /**
     * Get the list of all eureka service urls from DNS for the eureka client to
     * talk to. The client picks up the service url from its zone and then fails over to
     * other zones randomly. If there are multiple servers in the same zone, the client once
     * again picks one randomly. This way the traffic will be distributed in the case of failures.
     *
     * @param clientConfig the clientConfig to use
     * @param instanceZone The zone in which the client resides.
     * @param preferSameZone true if we have to prefer the same zone as the client, false otherwise.
     * @param randomizer a randomizer to randomized returned urls
     *
     * @return The list of all eureka service urls for the eureka client to talk to.
     */
    public static List<String> getServiceUrlsFromDNS(EurekaClientConfig clientConfig, String instanceZone, boolean preferSameZone, ServiceUrlRandomizer randomizer) {
        String region = getRegion(clientConfig);
        // Get zone-specific DNS names for the given region so that we can get a
        // list of available zones
        Map<String, List<String>> zoneDnsNamesMap = getZoneBasedDiscoveryUrlsFromRegion(clientConfig, region);
        Set<String> availableZones = zoneDnsNamesMap.keySet();
        List<String> zones = new ArrayList<String>(availableZones);
        if (zones.isEmpty()) {
            throw new RuntimeException("No available zones configured for the instanceZone " + instanceZone);
        }
        int zoneIndex = 0;
        boolean zoneFound = false;
        for (String zone : zones) {
            logger.debug("Checking if the instance zone {} is the same as the zone from DNS {}", instanceZone, zone);
            if (preferSameZone) {
                if (instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            } else {
                if (!instanceZone.equalsIgnoreCase(zone)) {
                    zoneFound = true;
                }
            }
            if (zoneFound) {
                logger.debug("The zone index from the list {} that matches the instance zone {} is {}",
                        zones, instanceZone, zoneIndex);
                break;
            }
            zoneIndex++;
        }
        if (zoneIndex >= zones.size()) {
            if (logger.isWarnEnabled()) {
                logger.warn("No match for the zone {} in the list of available zones {}",
                        instanceZone, zones.toArray());
            }
        } else {
            // Rearrange the zones with the instance zone first
            for (int i = 0; i < zoneIndex; i++) {
                String zone = zones.remove(0);
                zones.add(zone);
            }
        }

        // Now get the eureka urls for all the zones in the order and return it
        List<String> serviceUrls = new ArrayList<String>();
        for (String zone : zones) {
            for (String zoneCname : zoneDnsNamesMap.get(zone)) {
                List<String> ec2Urls = new ArrayList<String>(getEC2DiscoveryUrlsFromZone(zoneCname, DiscoveryUrlType.CNAME));
                // Rearrange the list to distribute the load in case of multiple servers
                if (ec2Urls.size() > 1) {
                    randomizer.randomize(ec2Urls);
                }
                for (String ec2Url : ec2Urls) {
                    StringBuilder sb = new StringBuilder()
                            .append("http://")
                            .append(ec2Url)
                            .append(":")
                            .append(clientConfig.getEurekaServerPort());
                    if (clientConfig.getEurekaServerURLContext() != null) {
                        if (!clientConfig.getEurekaServerURLContext().startsWith("/")) {
                            sb.append("/");
                        }
                        sb.append(clientConfig.getEurekaServerURLContext());
                        if (!clientConfig.getEurekaServerURLContext().endsWith("/")) {
                            sb.append("/");
                        }
                    } else {
                        sb.append("/");
                    }
                    String serviceUrl = sb.toString();
                    logger.debug("The EC2 url is {}", serviceUrl);
                    serviceUrls.add(serviceUrl);
                }
            }
        }
        // Rearrange the fail over server list to distribute the load
        String primaryServiceUrl = serviceUrls.remove(0);
        randomizer.randomize(serviceUrls);
        serviceUrls.add(0, primaryServiceUrl);

        if (logger.isDebugEnabled()) {
            logger.debug("This client will talk to the following serviceUrls in order : {} ",
                    (Object) serviceUrls.toArray());
        }
        return serviceUrls;
    }

    /**
     * Get the list of all eureka service urls from properties file for the eureka client to talk to.
     *
     * @param clientConfig the clientConfig to use
     * @param instanceZone The zone in which the client resides
     * @param preferSameZone true if we have to prefer the same zone as the client, false otherwise
     * @return The list of all eureka service urls for the eureka client to talk to
     */
    public static List<String> getServiceUrlsFromConfig(EurekaClientConfig clientConfig, String instanceZone, boolean preferSameZone) {
        List<String> orderedUrls = new ArrayList<String>();
        String region = getRegion(clientConfig);
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        if (availZones == null || availZones.length == 0) {
            availZones = new String[1];
            availZones[0] = DEFAULT_ZONE;
        }
        logger.debug("The availability zone for the given region {} are {}", region, availZones);
        int myZoneOffset = getZoneOffset(instanceZone, preferSameZone, availZones);

        List<String> serviceUrls = clientConfig.getEurekaServerServiceUrls(availZones[myZoneOffset]);
        if (serviceUrls != null) {
            orderedUrls.addAll(serviceUrls);
        }
        int currentOffset = myZoneOffset == (availZones.length - 1) ? 0 : (myZoneOffset + 1);
        while (currentOffset != myZoneOffset) {
            serviceUrls = clientConfig.getEurekaServerServiceUrls(availZones[currentOffset]);
            if (serviceUrls != null) {
                orderedUrls.addAll(serviceUrls);
            }
            if (currentOffset == (availZones.length - 1)) {
                currentOffset = 0;
            } else {
                currentOffset++;
            }
        }

        if (orderedUrls.size() < 1) {
            throw new IllegalArgumentException("DiscoveryClient: invalid serviceUrl specified!");
        }
        return orderedUrls;
    }

    /**
     * Get the list of all eureka service urls from properties file for the eureka client to talk to.
     *
     * @param clientConfig the clientConfig to use
     * @param instanceZone The zone in which the client resides
     * @param preferSameZone true if we have to prefer the same zone as the client, false otherwise
     * @return an (ordered) map of zone -> list of urls mappings, with the preferred zone first in iteration order
     */
    public static Map<String, List<String>> getServiceUrlsMapFromConfig(EurekaClientConfig clientConfig, String instanceZone, boolean preferSameZone) {
        Map<String, List<String>> orderedUrls = new LinkedHashMap<>();
        String region = getRegion(clientConfig);
        String[] availZones = clientConfig.getAvailabilityZones(clientConfig.getRegion());
        if (availZones == null || availZones.length == 0) {
            availZones = new String[1];
            availZones[0] = DEFAULT_ZONE;
        }
        logger.debug("The availability zone for the given region {} are {}", region, availZones);
        int myZoneOffset = getZoneOffset(instanceZone, preferSameZone, availZones);

        String zone = availZones[myZoneOffset];
        List<String> serviceUrls = clientConfig.getEurekaServerServiceUrls(zone);
        if (serviceUrls != null) {
            orderedUrls.put(zone, serviceUrls);
        }
        int currentOffset = myZoneOffset == (availZones.length - 1) ? 0 : (myZoneOffset + 1);
        while (currentOffset != myZoneOffset) {
            zone = availZones[currentOffset];
            serviceUrls = clientConfig.getEurekaServerServiceUrls(zone);
            if (serviceUrls != null) {
                orderedUrls.put(zone, serviceUrls);
            }
            if (currentOffset == (availZones.length - 1)) {
                currentOffset = 0;
            } else {
                currentOffset++;
            }
        }

        if (orderedUrls.size() < 1) {
            throw new IllegalArgumentException("DiscoveryClient: invalid serviceUrl specified!");
        }
        return orderedUrls;
    }

    /**
     * Get the list of EC2 URLs given the zone name.
     *
     * @param dnsName The dns name of the zone-specific CNAME
     * @param type CNAME or EIP that needs to be retrieved
     * @return The list of EC2 URLs associated with the dns name
     */
    public static Set<String> getEC2DiscoveryUrlsFromZone(String dnsName, DiscoveryUrlType type) {
        Set<String> eipsForZone = null;
        try {
            dnsName = "txt." + dnsName;
            logger.debug("The zone url to be looked up is {} :", dnsName);
            Set<String> ec2UrlsForZone = DnsResolver.getCNamesFromTxtRecord(dnsName);
            for (String ec2Url : ec2UrlsForZone) {
                logger.debug("The eureka url for the dns name {} is {}", dnsName, ec2Url);
            }
            if (DiscoveryUrlType.CNAME.equals(type)) {
                return ec2UrlsForZone;
            }
            eipsForZone = new TreeSet<String>();
            for (String cname : ec2UrlsForZone) {
                String[] tokens = cname.split("\\.");
                String ec2HostName = tokens[0];
                String[] ips = ec2HostName.split("-");
                StringBuilder eipBuffer = new StringBuilder();
                for (int ipCtr = 1; ipCtr < 5; ipCtr++) {
                    eipBuffer.append(ips[ipCtr]);
                    if (ipCtr < 4) {
                        eipBuffer.append(".");
                    }
                }
                eipsForZone.add(eipBuffer.toString());
            }
            logger.debug("The EIPS for {} is {} :", dnsName, eipsForZone);
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:" + dnsName, e);
        }
        return eipsForZone;
    }

    /**
     * Get the zone based CNAMES that are bound to a region.
     *
     * @param region
     *            - The region for which the zone names need to be retrieved
     * @return - The list of CNAMES from which the zone-related information can
     *         be retrieved
     */
    public static Map<String, List<String>> getZoneBasedDiscoveryUrlsFromRegion(EurekaClientConfig clientConfig, String region) {
        String discoveryDnsName = null;
        try {
            discoveryDnsName = "txt." + region + "." + clientConfig.getEurekaServerDNSName();

            logger.debug("The region url to be looked up is {} :", discoveryDnsName);
            Set<String> zoneCnamesForRegion = new TreeSet<String>(DnsResolver.getCNamesFromTxtRecord(discoveryDnsName));
            Map<String, List<String>> zoneCnameMapForRegion = new TreeMap<String, List<String>>();
            for (String zoneCname : zoneCnamesForRegion) {
                String zone = null;
                if (isEC2Url(zoneCname)) {
                    throw new RuntimeException(
                            "Cannot find the right DNS entry for "
                                    + discoveryDnsName
                                    + ". "
                                    + "Expected mapping of the format <aws_zone>.<domain_name>");
                } else {
                    String[] cnameTokens = zoneCname.split("\\.");
                    zone = cnameTokens[0];
                    logger.debug("The zoneName mapped to region {} is {}", region, zone);
                }
                List<String> zoneCnamesSet = zoneCnameMapForRegion.get(zone);
                if (zoneCnamesSet == null) {
                    zoneCnamesSet = new ArrayList<String>();
                    zoneCnameMapForRegion.put(zone, zoneCnamesSet);
                }
                zoneCnamesSet.add(zoneCname);
            }
            return zoneCnameMapForRegion;
        } catch (Throwable e) {
            throw new RuntimeException("Cannot get cnames bound to the region:" + discoveryDnsName, e);
        }
    }

    /**
     * Get the region that this particular instance is in.
     *
     * @return - The region in which the particular instance belongs to.
     */
    public static String getRegion(EurekaClientConfig clientConfig) {
        String region = clientConfig.getRegion();
        if (region == null) {
            region = DEFAULT_REGION;
        }
        region = region.trim().toLowerCase();
        return region;
    }

    // FIXME this is no valid for vpc
    private static boolean isEC2Url(String zoneCname) {
        return zoneCname.startsWith("ec2");
    }

    /**
     * Gets the zone to pick up for this instance.
     */
    private static int getZoneOffset(String myZone, boolean preferSameZone, String[] availZones) {
        for (int i = 0; i < availZones.length; i++) {
            if (myZone != null && (availZones[i].equalsIgnoreCase(myZone.trim()) == preferSameZone)) {
                return i;
            }
        }
        logger.warn("DISCOVERY: Could not pick a zone based on preferred zone settings. My zone - {}," +
                " preferSameZone - {}. Defaulting to {}", myZone, preferSameZone, availZones[0]);

        return 0;
    }
}

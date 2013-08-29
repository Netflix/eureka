/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import com.netflix.eureka.cluster.PeerEurekaNode;
import com.netflix.servo.monitor.Monitors;

/**
 * An AWS specific <em>elastic ip</em> binding utility for binding eureka
 * servers for a well known <code>IP address</code>.
 * 
 * <p>
 * <em>Eureka</em> clients talk to <em>Eureka</em> servers bound with well known
 * <code>IP addresses</code> since that is the most reliable mechanism to
 * discover the <em>Eureka</em> servers. When Eureka servers come up they bind
 * themselves to a well known <em>elastic ip</em>
 * </p>
 * 
 * <p>
 * This binding mechanism gravitates towards one eureka server per zone for
 * resilience.Atleast one elastic ip should be slotted for each eureka server in
 * a zone. If more than eureka server is launched per zone and there are not
 * enough elastic ips slotted, the server tries to pick a free EIP slotted for other
 * zones and if it still cannot find a free EIP, waits and keeps trying.
 * </p>
 * 
 * @author Karthik Ranganathan, Greg Kim
 * 
 */
public class EIPManager {
    private static final int HEARTBEAT_RETRY_INTERVAL_MS = 300;
    private static final int NO_HEARTBEAT_RETRIES = 3;
    private static final String US_EAST_1 = "us-east-1";
    private static final Logger logger = LoggerFactory
    .getLogger(EIPManager.class);
    private EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
    .getInstance().getConfiguration();

    private static final EIPManager s_instance = new EIPManager();

    public static EIPManager getInstance() {
        return s_instance;
    }

    private EIPManager() {
        try {

            Monitors.registerObject(this);

        } catch (Throwable e) {
            logger.warn(
                    "Cannot register the JMX monitor for the InstanceRegistry :",
                    e);
        }
    }

    /**
     * Binds an EIP to this instance. if an EIP is already bound to this
     * instance this method simply returns. Otherwise, this method tries to find
     * an unused EIP based on the registry information. If it cannot find any
     * unused EIP this method, it waits until a free EIP is available.
     * 
     */
    public void bindToEIP() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        String myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.instanceId);
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.availabilityZone);
        String myPublicIP = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.publicIpv4);

        String selectedEIP = getCandidateEIP(myInstanceId, myZone, myPublicIP);

        if (selectedEIP == null) {
            // The EIP is already bound to this instance
            logger.debug("No need to bind to EIP");
            return;
        } else {
            try {
                AmazonEC2 ec2Service = getEC2Service();
                AssociateAddressRequest request = new AssociateAddressRequest(
                        myInstanceId, selectedEIP);
                ec2Service.associateAddress(request);
                logger.info("\n\n\nAssociated " + myInstanceId
                        + " running in zone: " + myZone + " to elastic IP: "
                        + selectedEIP);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to bind elastic IP: "
                        + selectedEIP + " to " + myInstanceId, t);
            }
        }
    }

    /**
     * Unbind the EIP that this instance is associated with.
     */
    public void unbindEIP() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        String myPublicIP = null;
        if (myInfo != null
                && myInfo.getDataCenterInfo().getName() == Name.Amazon) {
            myPublicIP = ((AmazonInfo) myInfo.getDataCenterInfo())
            .get(MetaDataKey.publicIpv4);
            try {
                AmazonEC2 ec2Service = getEC2Service();
                DisassociateAddressRequest dissociateRequest = new DisassociateAddressRequest()
                .withPublicIp(myPublicIP);
                ec2Service.disassociateAddress(dissociateRequest);
                logger.info("Dissociated the EIP {} from this instance",
                        myPublicIP);
            } catch (Throwable e) {
                throw new RuntimeException("Cannot dissociate address"
                        + myPublicIP + "from this instance", e);
            }
        }

    }

    /**
     * Get the EIP for this instance to bind to.
     * 
     * <p>
     * if an EIP is already bound to this instance this method simply returns.
     * Otherwise, this method tries to find an unused EIP based on the registry
     * information. If it cannot find any unused EIP, it waits until a free EIP is available.
     * </p>
     * 
     * @param myInstanceId
     *            the instance id for this instance
     * @param myZone
     *            the zone where this instance is in.
     * @param myPublicIP
     *            the public ip of this instance
     * @return null if the EIP is already bound, valid EIP otherwise.
     */
    public String getCandidateEIP(String myInstanceId, String myZone, String myPublicIP) {

        if (myZone == null) {
            myZone = "us-east-1d";
            myPublicIP = "us-east-1d";
        }
        Collection<String> eipCandidates = (DiscoveryManager.getInstance()
                .getEurekaClientConfig().shouldUseDnsForFetchingServiceUrls() ? getEIPsForZoneFromDNS(myZone)
                        : getEIPsForZoneFromConfig(myZone));

        if (eipCandidates == null || eipCandidates.size() == 0) {
            throw new RuntimeException(
                    "Could not get any elastic ips from the EIP pool for zone :"
                    + myZone);
        }
        List<String> availableEIPList = new ArrayList<String>();
        for (String eip : eipCandidates) {
            String eipTrimmed = eip.trim();
            /* It's possible for myPublicIP to be null when eureka is restarted
             * because unbinding an EIP on Amazon results in a null IP for the
             * instance for a few minutes. In that case, it's ok to rebind the
             * new EIP.
             */
            if (myPublicIP != null && myPublicIP.equals(eipTrimmed)) {
                // Already associated to an EIP?
                logger.debug("Already bound to an EIP : " + eip);
                return null;
            }
            availableEIPList.add(eipTrimmed);
        }
        // Look for unused EIPs
        InstanceInfo instanceInfo = ApplicationInfoManager.getInstance().getInfo();
        Application app = DiscoveryManager.getInstance().getDiscoveryClient()
        .getApplication(instanceInfo.getAppName());

        List<String> unreachableEips = new ArrayList<String>();

        if (app != null) {
            for (InstanceInfo i : app.getInstances()) {
                // Avoid eureka servers from other regions
                if (!PeerAwareInstanceRegistry.getInstance().isRegisterable(i)) {
                    continue;
                }
                AmazonInfo amazonInfo = (AmazonInfo) i.getDataCenterInfo();
                String publicIP = amazonInfo.get(MetaDataKey.publicIpv4);
                String instanceId = amazonInfo.get(MetaDataKey.instanceId);
                if ((instanceId.equals(myInstanceId))
                        && (availableEIPList.contains(publicIP))) {
                    // This can happen if the server restarts and we haven't
                    // fully been dissociated with the previous shutdown
                    logger.warn(
                            "The instance id {} is already bound to EIP {}. Hence returning that.",
                            myInstanceId, publicIP);
                    return publicIP;
                }
                logger.info("The list of available EIPS in the priority order :"
                        + availableEIPList);
                for (Iterator<String> it = availableEIPList.iterator(); it.hasNext(); ) {
                    String eip = it.next();
                    // if there is already an EIP association, remove it from
                    // the list if it is reachable
                    if ((eip.trim().equals(publicIP))) {
                        String instanceHostName = i.getHostName();
                        PeerEurekaNode replicaNode = getPeerEurekaNode(instanceHostName);
                        if (replicaNode == null) {
                            logger.warn("Cannot get peer eureka node. Is this even a peer? :"
                                    + instanceHostName);
                            continue;
                        }
                        boolean isInstanceReachable = false;
                        for (int ctr = 0; ctr < NO_HEARTBEAT_RETRIES; ctr++) {
                            try {
                                replicaNode.heartbeat(
                                        instanceInfo.getAppName(),
                                        instanceInfo.getId(), instanceInfo,
                                        null, true);
                                isInstanceReachable = true;
                                break;
                            } catch (Throwable e) {
                                logger.warn(
                                        String.format(
                                                "Error while checking whether the EIP %s is used by the instance id %s as reported by eureka peer.",
                                                eip, instanceInfo.getId()), e);
                                try {
                                    Thread.sleep(HEARTBEAT_RETRY_INTERVAL_MS);
                                } catch (InterruptedException e1) {
                                    logger.warn("Interrupted while trying send heartbeat", e1);
                                }
                            }
                        }

                        if (!isInstanceReachable) {
                            logger.info(
                                    "The instance {} seems unreachable, making EIP: {} available for reuse.",
                                    instanceInfo.getHostName(), eip);
                            it.remove(); // This is so that we add this at the end, prioritize EIPs which are not even taken by an instance according to peer nodes.
                            unreachableEips.add(eip);
                            continue;
                        }
                        logger.info("Removing the EIP {} as it is already used by instance {}", eip, instanceId);
                        it.remove();
                    }
                }
            }
        }

        /**
         * The available EIPs list (when retrieved from DNS) contains EIPs for "this zone (i.e. in which this instance
         * is running)" first and then from other zone. In the above loop we remove all the EIPs which are bound to
         * eureka but are not reachable, these EIPs are added in unreachableEips list. Here, we add this unreachable
         * EIPs at the end so that we pick these when no EIP is available readily. Unreachable EIPs are also sorted
         * such that local zone is first, so the eventual list always have the same Zone EIP first.
         */

        if (availableEIPList == null) {
            throw new RuntimeException("Cannot find a free EIP to bind");
        } else {
            availableEIPList.addAll(unreachableEips);
            if (!unreachableEips.isEmpty()) {
                logger.info("Added unreachable EIPs {} to the available EIP list. Final EIP list is {}", unreachableEips, availableEIPList);
            }
            if (availableEIPList.isEmpty()) {
                throw new RuntimeException("Cannot find a free EIP to bind");
            }
        }
        return availableEIPList.iterator().next();
    }

    /**
     * Return the  @link {PeerEurekaNode} given the host name.
     * @param instanceHostName - the host name for which the @link {PeerEurekaNode}.
     * @return @link {PeerEurekaNode} object that represents this host name.
     */
    private PeerEurekaNode getPeerEurekaNode(String instanceHostName) {
        PeerEurekaNode replicaNode = null;
        try {
            for (PeerEurekaNode node : PeerAwareInstanceRegistry
                    .getInstance().getReplicaNodes()) {
                if (new URL(node.getServiceUrl()).getHost()
                        .equalsIgnoreCase(instanceHostName)) {
                    replicaNode = node;
                }
            }
        } catch (Throwable e) {
            logger.error(
                    "Cannot get peer eureka node instance from instanceHostName for "
                    + instanceHostName, replicaNode);
        }
        return replicaNode;
    }

    /**
     * Get the list of EIPs from the configuration.
     * 
     * @param myZone
     *            - the zone in which the instance resides.
     * @return collection of EIPs to choose from for binding.
     */
    private Collection<String> getEIPsForZoneFromConfig(String myZone) {
        List<String> ec2Urls = DiscoveryManager.getInstance()
        .getEurekaClientConfig().getEurekaServerServiceUrls(myZone);
        return getEIPsFromServiceUrls(ec2Urls);
    }

    /**
     * Get the list of EIPs from the ec2 urls.
     * 
     * @param ec2Urls
     *            the ec2urls for which the EIP needs to be obtained.
     * @return collection of EIPs.
     */
    private Collection<String> getEIPsFromServiceUrls(List<String> ec2Urls) {
        List<String> returnedUrls = new ArrayList<String>();
        String region = DiscoveryManager.getInstance().getEurekaClientConfig().getRegion();
        String regionPhrase="";
        if (!US_EAST_1.equals(region)) {
            regionPhrase = "." + region;
        }
        for (String cname : ec2Urls) {
            int beginIndex = cname.indexOf("ec2-") + 4;
            int endIndex = cname.indexOf(regionPhrase + ".compute");
            String eipStr = cname.substring(beginIndex, endIndex);
            String eip = eipStr.replaceAll("\\-", ".");
            returnedUrls.add(eip);
        }
        return returnedUrls;
    }

    /**
     * Get the list of EIPS from the DNS.
     * 
     * <p>
     * This mechanism looks for the EIP pool in the zone the instance is in by
     * looking up the DNS name <code>{zone}.{region}.{domainName}</code>. The
     * zone is fetched from the {@link InstanceInfo} object;the region is picked
     * up from the specified configuration
     * {@link EurekaServerConfig#getRegion()};the domain name is picked up from
     * the specified configuration {@link EurekaServerConfig#getDomainName()}.
     * </p>
     * 
     * @param myZone
     *            the zone where this instance exist in.
     * @return the collection of EIPs that exist in the zone this instance is
     *         in.
     */
    private Collection<String> getEIPsForZoneFromDNS(String myZone) {
        List<String> ec2Urls = DiscoveryManager.getInstance()
        .getDiscoveryClient().getServiceUrlsFromDNS(myZone, true);
        return getEIPsFromServiceUrls(ec2Urls);
    }

    /**
     * Gets the EC2 service object to call AWS APIs.
     * 
     * @return the EC2 service object to call AWS APIs.
     */
    private AmazonEC2 getEC2Service() {
        eurekaConfig = EurekaServerConfigurationManager.getInstance()
        .getConfiguration();

        String aWSAccessId = eurekaConfig.getAWSAccessId();
        String aWSSecretKey = eurekaConfig.getAWSSecretKey();

        AmazonEC2 ec2Service;
        if (null != aWSAccessId && !"".equals(aWSAccessId) &&
                null != aWSSecretKey && !"".equals(aWSSecretKey)) {
            ec2Service = new AmazonEC2Client(new BasicAWSCredentials(
                    aWSAccessId, aWSSecretKey));
        }
        else
        {
            ec2Service = new AmazonEC2Client(
                    new InstanceProfileCredentialsProvider());
        }

        String region = DiscoveryManager.getInstance().getEurekaClientConfig()
        .getRegion();
        region = region.trim().toLowerCase();
        ec2Service.setEndpoint("ec2." + region + ".amazonaws.com");
        return ec2Service;
    }
}

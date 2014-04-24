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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryManager;
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
     * Checks if an EIP is already bound to the instance
     * @return true if an EIP is bound, false otherwise
     */
    public boolean isEIPBound() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        String myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.instanceId);
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.availabilityZone);
        String myPublicIP = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.publicIpv4);

        Collection<String> candidateEIPs = getCandidateEIPs(myInstanceId,
                myZone);
        for (String eipEntry : candidateEIPs) {
            if (eipEntry.equals(myPublicIP)) {
                logger.info(
                        "My instance {} seems to be already associated with the public ip {}",
                        myInstanceId, myPublicIP);
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if an EIP is bound and optionally binds the EIP.
     *
     * The list of EIPs are arranged with the EIPs allocated in the zone first
     * followed by other EIPs.
     *
     * If an EIP is already bound to this instance this method simply returns. Otherwise, this method tries to find
     * an unused EIP based on information from AWS. If it cannot find any unused EIP this method, it will be retried
     * for a specified interval.
     *
     * One of the following scenarios can happen here :
     *
     *  1) If the instance is already bound to an EIP as deemed by AWS, no action is taken.
     *  2) If an EIP is already bound to another instance as deemed by AWS, that EIP is skipped.
     *  3) If an EIP is not already bound to an instance and if this instance is not bound to an EIP, then
     *     the EIP is bound to this instance.
     */
    public void bindEIP( ) {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        String myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.instanceId);
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo())
        .get(MetaDataKey.availabilityZone);

        Collection<String> candidateEIPs = getCandidateEIPs(myInstanceId,
                myZone);

        AmazonEC2 ec2Service = getEC2Service();
        boolean isMyinstanceAssociatedWithEIP = false;
        String selectedEIP = null;


        for (String eipEntry : candidateEIPs) {
            try {
                String associatedInstanceId = null;

                // Check with AWS, if this EIP is already been used by another
                // instance
                DescribeAddressesRequest describeAddressRequest = new DescribeAddressesRequest()
                .withPublicIps(eipEntry);
                DescribeAddressesResult result = ec2Service
                .describeAddresses(describeAddressRequest);
                if ((result.getAddresses() != null)
                        && (!result.getAddresses().isEmpty())) {
                    associatedInstanceId = result.getAddresses().get(0)
                    .getInstanceId();
                    // This EIP is not used by any other instance, hence mark it for selection if it is not
                    // already marked.
                    if (((associatedInstanceId == null) || (associatedInstanceId
                            .isEmpty()))) {
                        if (selectedEIP == null) {
                            selectedEIP = eipEntry;
                        }
                    }
                    // This EIP is associated with an instance, check if this is the same as the current instance.
                    // If it is the same, stop searching for an EIP as this instance is already associated with an EIP
                    else if (isMyinstanceAssociatedWithEIP = (associatedInstanceId
                            .equals(myInstanceId))) {
                        selectedEIP = eipEntry;
                        break;
                    }
                    // The EIP is used by some other instance, hence skip it
                    else {
                        logger.warn(
                                "The selected EIP {} is associated with another instance {} according to AWS, hence skipping this",
                                eipEntry, associatedInstanceId);
                        continue;
                    }
                }
            } catch (Throwable t) {
                logger.error("Failed to bind elastic IP: " + eipEntry + " to "
                        + myInstanceId, t);
            }
        }
        // Only bind if the EIP is already associated
        if (!isMyinstanceAssociatedWithEIP) {
            AssociateAddressRequest associateAddressRequest = new AssociateAddressRequest(
                    myInstanceId, selectedEIP);

            ec2Service.associateAddress(associateAddressRequest);
            logger.info("\n\n\nAssociated " + myInstanceId
                    + " running in zone: " + myZone + " to elastic IP: "
                    + selectedEIP);

         }
            logger.info(
                    "My instance {} seems to be already associated with the EIP {}",
                    myInstanceId, selectedEIP);
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
     * Get the list of EIPs in the order of preference depending on instance zone
     *
     * @param myInstanceId
     *            the instance id for this instance
     * @param myZone
     *            the zone where this instance is in
     * @return Collection containing the list of available EIPs
     */
    public Collection<String> getCandidateEIPs(String myInstanceId, String myZone) {

        if (myZone == null) {
            myZone = "us-east-1d";
       }
        Collection<String> eipCandidates = (DiscoveryManager.getInstance()
                .getEurekaClientConfig().shouldUseDnsForFetchingServiceUrls() ? getEIPsForZoneFromDNS(myZone)
                        : getEIPsForZoneFromConfig(myZone));

        if (eipCandidates == null || eipCandidates.size() == 0) {
            throw new RuntimeException(
                    "Could not get any elastic ips from the EIP pool for zone :"
                    + myZone);
        }

        return eipCandidates;
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

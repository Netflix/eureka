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

package com.netflix.eureka.aws;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.Address;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesRequest;
import com.amazonaws.services.ec2.model.DescribeAddressesResult;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages EIP bindings in EC2 classic.
 */
@Singleton
public class Ec2ClassicEIPManager extends AbstractEipManager {
    private static final String US_EAST_1 = "us-east-1";

    private static final Logger logger = LoggerFactory.getLogger(Ec2ClassicEIPManager.class);

    private final EurekaClientConfig eurekaClientConfig;
    private final EurekaClient eurekaClient;
    private final ApplicationInfoManager infoManager;
    private final AmazonEC2 amazonEC2;

    @Inject
    public Ec2ClassicEIPManager(EurekaClientConfig eurekaClientConfig,
                                EurekaServerConfig eurekaServerConfig,
                                EurekaClient eurekaClient,
                                ApplicationInfoManager infoManager,
                                PeerAwareInstanceRegistry registry,
                                AmazonEC2 amazonEC2) {
        super(eurekaServerConfig, registry);
        this.eurekaClientConfig = eurekaClientConfig;
        this.eurekaClient = eurekaClient;
        this.infoManager = infoManager;
        this.amazonEC2 = amazonEC2;
    }

    @Override
    public boolean isEIPBound() {
        InstanceInfo myInfo = infoManager.getInfo();
        String myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.instanceId);
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.availabilityZone);
        String myPublicIP = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.publicIpv4);

        Collection<String> candidateEIPs = getCandidateEIPs(myInstanceId, myZone);
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

    @Override
    public void bindEIP() {
        InstanceInfo myInfo = infoManager.getInfo();
        String myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.instanceId);
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.availabilityZone);

        Collection<String> candidateEIPs = getCandidateEIPs(myInstanceId, myZone);

        boolean isMyinstanceAssociatedWithEIP = false;
        Address selectedEIP = null;


        for (String eipEntry : candidateEIPs) {
            try {
                String associatedInstanceId;

                // Check with AWS, if this EIP is already been used by another instance
                DescribeAddressesRequest describeAddressRequest = new DescribeAddressesRequest().withPublicIps(eipEntry);
                DescribeAddressesResult result = amazonEC2.describeAddresses(describeAddressRequest);
                if ((result.getAddresses() != null) && (!result.getAddresses().isEmpty())) {
                    Address eipAddress = result.getAddresses().get(0);
                    associatedInstanceId = eipAddress.getInstanceId();
                    // This EIP is not used by any other instance, hence mark it for selection if it is not
                    // already marked.
                    if (((associatedInstanceId == null) || (associatedInstanceId.isEmpty()))) {
                        if (selectedEIP == null) {
                            selectedEIP = eipAddress;
                        }
                    } else if (isMyinstanceAssociatedWithEIP = (associatedInstanceId.equals(myInstanceId))) {
                        // This EIP is associated with an instance, check if this is the same as the current instance.
                        // If it is the same, stop searching for an EIP as this instance is already associated with an
                        // EIP
                        selectedEIP = eipAddress;
                        break;
                    } else {
                        // The EIP is used by some other instance, hence skip it
                        logger.warn(
                                "The selected EIP {} is associated with another instance {} according to AWS, hence "
                                        + "skipping this", eipEntry, associatedInstanceId);
                    }
                }
            } catch (Throwable t) {
                logger.error("Failed to bind elastic IP: " + eipEntry + " to "
                        + myInstanceId, t);
            }
        }
        if (null != selectedEIP) {
            String publicIp = selectedEIP.getPublicIp();
            // Only bind if the EIP is not already associated
            if (!isMyinstanceAssociatedWithEIP) {

                AssociateAddressRequest associateAddressRequest = new AssociateAddressRequest().withInstanceId(
                        myInstanceId);

                String domain = selectedEIP.getDomain();
                if ("vpc".equals(domain)) {
                    associateAddressRequest.setAllocationId(selectedEIP.getAllocationId());
                } else {
                    associateAddressRequest.setPublicIp(publicIp);
                }

                amazonEC2.associateAddress(associateAddressRequest);
                logger.info("\n\n\nAssociated " + myInstanceId + " running in zone: " + myZone + " to elastic IP: "
                        + publicIp);
            }
            logger.info("My instance {} seems to be already associated with the EIP {}", myInstanceId, publicIp);
        } else {
            logger.info("No EIP is free to be associated with this instance. Candidate EIPs are: " + candidateEIPs);
        }
    }

    @Override
    public void unbindEIP() {
        InstanceInfo myInfo = infoManager.getInfo();
        String myPublicIP;
        if (myInfo != null && myInfo.getDataCenterInfo().getName() == Name.Amazon) {
            myPublicIP = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.publicIpv4);
            try {
                DescribeAddressesRequest describeAddressRequest = new DescribeAddressesRequest().withPublicIps(myPublicIP);
                DescribeAddressesResult result = amazonEC2.describeAddresses(describeAddressRequest);
                if (result.getAddresses() != null && !result.getAddresses().isEmpty()) {
                    Address eipAddress = result.getAddresses().get(0);
                    DisassociateAddressRequest dissociateRequest = new DisassociateAddressRequest();
                    String domain = eipAddress.getDomain();
                    if ("vpc".equals(domain)) {
                        dissociateRequest.setAssociationId(eipAddress.getAssociationId());
                    } else {
                        dissociateRequest.setPublicIp(eipAddress.getPublicIp());
                    }

                    amazonEC2.disassociateAddress(dissociateRequest);
                    logger.info("Dissociated the EIP {} from this instance", myPublicIP);
                }
            } catch (Throwable e) {
                throw new RuntimeException("Cannot dissociate address" + myPublicIP + "from this instance", e);
            }
        }
    }

    @Override
    public Collection<String> getCandidateEIPs(String myInstanceId, String myZone) {
        if (myZone == null) {
            myZone = "us-east-1d";
        }

        Collection<String> eipCandidates = eurekaClientConfig.shouldUseDnsForFetchingServiceUrls()
                ? getEIPsForZoneFromDNS(myZone)
                : getEIPsForZoneFromConfig(myZone);

        if (eipCandidates == null || eipCandidates.isEmpty()) {
            throw new RuntimeException("Could not get any elastic ips from the EIP pool for zone :" + myZone);
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
        List<String> ec2Urls = eurekaClientConfig.getEurekaServerServiceUrls(myZone);
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
        String region = eurekaClientConfig.getRegion();
        String regionPhrase = "";
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
     * {@link com.netflix.discovery.EurekaClientConfig#getRegion()};the domain name is picked up from
     * the specified configuration {@link com.netflix.discovery.EurekaClientConfig#getEurekaServerDNSName()}
     * with a "txt." prefix (see {@link com.netflix.discovery.DiscoveryClient#getZoneBasedDiscoveryUrlsFromRegion}).
     * </p>
     *
     * @param myZone
     *            the zone where this instance exist in.
     * @return the collection of EIPs that exist in the zone this instance is
     *         in.
     */
    private Collection<String> getEIPsForZoneFromDNS(String myZone) {
        List<String> ec2Urls = eurekaClient.getServiceUrlsFromDNS(myZone, true);
        return getEIPsFromServiceUrls(ec2Urls);
    }
}

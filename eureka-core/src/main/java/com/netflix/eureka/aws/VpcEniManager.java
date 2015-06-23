package com.netflix.eureka.aws;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AttachNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeNetworkInterfacesResult;
import com.amazonaws.services.ec2.model.DetachNetworkInterfaceRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceNetworkInterface;
import com.amazonaws.services.ec2.model.NetworkInterface;
import com.amazonaws.services.ec2.model.Tag;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class VpcEniManager extends AbstractEipManager {

    private static final Logger logger = LoggerFactory.getLogger(VpcEniManager.class);

    private static final Pattern VPC_DNS_RE = Pattern.compile(".*vpc-(\\d+)-(\\d+)-(\\d+)-(\\d+)[.].*");

    /**
     * It is expected that ENIs allocated to Eureka cluster are tagged with 'Discovery' name.
     */
    public static final String TAG_NAME = "Name";
    public static final String TAG_NAME_VALUE = "Discovery";

    private final EurekaClientConfig eurekaClientConfig;
    private final EurekaClient eurekaClient;
    private final ApplicationInfoManager infoManager;
    private final AmazonEC2 amazonEC2;

    @Inject
    public VpcEniManager(EurekaClientConfig eurekaClientConfig,
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

        Set<String> localIpAddresses = getLocalIpAddresses();
        Collection<String> candidateEIPs = getCandidateEIPs(myInstanceId, myZone);

        for (String candidate : candidateEIPs) {
            if (localIpAddresses.contains(candidate)) {
                logger.info(
                        "My instance {} seems to be already associated with the ENI IP {}",
                        myInstanceId, candidate);
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

        List<NetworkInterface> eurekaENIs = getAvailableENIs(amazonEC2, myZone);
        logger.info("Found {} available Eureka ENIs", eurekaENIs);

        try {
            bind(amazonEC2, eurekaENIs.get(0), myInstanceId);
        } catch (Throwable e) {
            logger.error("Cannot bind ENI to the local instance", e);
        }
    }

    @Override
    public void unbindEIP() {
        InstanceInfo myInfo = infoManager.getInfo();
        String myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.instanceId);
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.availabilityZone);

        try {
            unbind(amazonEC2, myInstanceId, myZone);
        } catch (Throwable e) {
            logger.error("Cannot unbind ENI from the local instance", e);
        }
    }

    @Override
    public Collection<String> getCandidateEIPs(String myInstanceId, String myZone2) {
        InstanceInfo myInfo = infoManager.getInfo();
        String myZone = ((AmazonInfo) myInfo.getDataCenterInfo()).get(MetaDataKey.availabilityZone);

        Collection<String> eipCandidates = eurekaClientConfig.shouldUseDnsForFetchingServiceUrls()
                ? getEniIpsForZoneFromDNS(myZone)
                : getEniIpsForZoneFromConfig(myZone);

        if (eipCandidates == null || eipCandidates.isEmpty()) {
            throw new RuntimeException("Could not get any elastic ips from the EIP pool for zone :" + myZone);
        }
        return eipCandidates;
    }

    private Collection<String> getEniIpsForZoneFromConfig(String myZone) {
        List<String> serviceUrls = eurekaClientConfig.getEurekaServerServiceUrls(myZone);
        return getEniPrivateIpsFromServiceUrls(serviceUrls);
    }

    private List<String> getEniIpsForZoneFromDNS(String myZone) {
        List<String> serviceUrls = eurekaClient.getServiceUrlsFromDNS(myZone, true);
        return getEniPrivateIpsFromServiceUrls(serviceUrls);
    }

    private static List<String> getEniPrivateIpsFromServiceUrls(List<String> serviceUrls) {
        List<String> privateIps = new ArrayList<>();
        for (String serviceUrl : serviceUrls) {
            String privateIp = getEniPrivateIpFromServiceUrl(serviceUrl);
            if (privateIp != null) {
                privateIps.add(privateIp);
            }
        }
        return privateIps;
    }

    private static String getEniPrivateIpFromServiceUrl(String serviceUrl) {
        Matcher matcher = VPC_DNS_RE.matcher(serviceUrl);
        if (!matcher.matches()) {
            logger.warn("Service URL {} is not VPC DNS entry in the expected format vpc-xxx-xxx-xxx-xxx.<eureka_domain>", serviceUrl);
            return null;
        }
        return matcher.group(1) + '.' + matcher.group(2) + '.' + matcher.group(3) + '.' + matcher.group(4);
    }

    private static void bind(AmazonEC2 amazonEC2, NetworkInterface nic, String instanceId) {
        AttachNetworkInterfaceRequest request = new AttachNetworkInterfaceRequest();
        request.setDeviceIndex(1);
        request.setInstanceId(instanceId);
        request.setNetworkInterfaceId(nic.getNetworkInterfaceId());
        amazonEC2.attachNetworkInterface(request);
        logger.info("ENI {} attached to the local instance {}", nic.getNetworkInterfaceId(), instanceId);
    }

    private void unbind(AmazonEC2 amazonEC2, String instanceId, String myZone) {
        InstanceNetworkInterface eniNic = getLocallyBoundEniFromDiscoveryPool(amazonEC2, instanceId, myZone);
        if (eniNic == null) {
            logger.warn("Local instance {} has no discovery ENI attached; ENI unbinding not needed", instanceId);
            return;
        }

        DetachNetworkInterfaceRequest request = new DetachNetworkInterfaceRequest();
        request.setAttachmentId(eniNic.getAttachment().getAttachmentId());
        amazonEC2.detachNetworkInterface(request);
        logger.info("ENI {}/{} binding removed", eniNic.getNetworkInterfaceId(), eniNic.getPrivateIpAddress());
    }

    private static List<NetworkInterface> getAvailableENIs(AmazonEC2 amazonEC2, String myZone) {
        DescribeNetworkInterfacesResult networkInterfaces = amazonEC2.describeNetworkInterfaces();
        List<NetworkInterface> availableNICs = new ArrayList<>();
        for (NetworkInterface nic : networkInterfaces.getNetworkInterfaces()) {
            if (nic.getAvailabilityZone().equals(myZone)) {
                for (Tag tag : nic.getTagSet()) {
                    if (tag.getKey().equals(TAG_NAME) && tag.getValue().equals(TAG_NAME_VALUE)) {
                        if (nic.getAttachment() == null) {
                            availableNICs.add(nic);
                            logger.info("Found not attached Eureka NIC {}", nic.getNetworkInterfaceId());
                        } else {
                            logger.info("Eureka NIC {} is already attached with an instance", nic.getNetworkInterfaceId(), nic.getAttachment().getInstanceId());
                        }
                        break;
                    }
                }
            }
        }
        return availableNICs;
    }

    private InstanceNetworkInterface getLocallyBoundEniFromDiscoveryPool(AmazonEC2 amazonEC2, String instanceId, String myZone) {
        Set<String> candidateEIPs = new HashSet<>(getCandidateEIPs(instanceId, myZone));

        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Collections.singletonList(instanceId));
        DescribeInstancesResult result = amazonEC2.describeInstances(request);

        if (!result.getReservations().isEmpty()) {
            Instance instance = result.getReservations().get(0).getInstances().get(0);
            for (InstanceNetworkInterface networkInterface : instance.getNetworkInterfaces()) {
                if (candidateEIPs.contains(networkInterface.getPrivateIpAddress())) {
                    return networkInterface;
                }
            }
        }
        logger.info("Local instance {} has no second ENI interface attached", instanceId);
        return null;
    }

    private static Set<String> getLocalIpAddresses() {
        Set<String> localIpAddressess = new HashSet<>();
        try {
            Enumeration<java.net.NetworkInterface> nicIter = java.net.NetworkInterface.getNetworkInterfaces();
            while (nicIter.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = nicIter.nextElement();
                Enumeration<InetAddress> inetAddressesIt = networkInterface.getInetAddresses();
                while (inetAddressesIt.hasMoreElements()) {
                    localIpAddressess.add(inetAddressesIt.nextElement().getHostAddress());
                }
            }
        } catch (SocketException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Cannot probe network interfaces on the local node", e);
            }
        }
        return localIpAddressess;
    }
}

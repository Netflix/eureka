/*
 * EIPManager.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/util/EIPManager.java#2 $ 
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.util;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.apache.commons.configuration.ConfigurationException;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AssociateAddressRequest;
import com.amazonaws.services.ec2.model.DisassociateAddressRequest;
import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryClient.DiscoveryUrlType;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.shared.Application;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.Monitors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Responsible for binding DS instance to appropriate EIPs depending on which
 * zone they are in and which instances are already running on that zone if at
 * all.
 * 
 * @author gkim
 */
public class EIPManager implements EIPManagerMBean {
    private static final String NETFLIX_DISCOVERY_EIP_AWS_SECRET_KEY = "netflix.discovery.eip.AWSSecretKey";
    private static final String NETFLIX_DISCOVERY_EIP_AWS_ACCESS_ID = "netflix.discovery.eip.AWSAccessId";
    private static final String PROP_DISCOVERY_DOMAIN_NAME = "netflix.discovery.domainName";
    private static final String PROP_EIP_LOOKUP_DNS = "netflix.discovery.eipLookupFromDns";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String REGION = "EC2_REGION";
    private static final EIPManager s_instance = new EIPManager();
    // private static final String PROP_EIP = "netflix.discovery.eip.";
    private static final Logger s_logger = LoggerFactory.getLogger(EIPManager.class); 
    private static final String DEFAULT_ZONE = "us-east-1a";
    private static final String PROP_EIP = "netflix.discovery.eip.";

    public static EIPManager getInstance() {
        return s_instance;
    }

    private EIPManager() {
        try {
            
            DefaultMonitorRegistry.getInstance().register(Monitors.newObjectMonitor("1", this));
          
         } catch (Throwable e) {
             s_logger.warn(
                     "Cannot register the JMX monitor for the InstanceRegistry :"
                            , e);
         }
    }

    /**
     * Bind to appropriate elastic ip based on which zone we're in.
     */
    public void bindToEIP() {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        String myInstanceId = null;
        String myZone = DEFAULT_ZONE;
        String myPublicIP = null;

        if (myInfo != null
                && myInfo.getDataCenterInfo().getName() == Name.Amazon) {
            myZone = ((AmazonInfo) myInfo.getDataCenterInfo())
                    .get(MetaDataKey.availabilityZone);
            myInstanceId = ((AmazonInfo) myInfo.getDataCenterInfo())
                    .get(MetaDataKey.instanceId);
            myPublicIP = ((AmazonInfo) myInfo.getDataCenterInfo())
                    .get(MetaDataKey.publicIpv4);
        } else {
            s_logger.info("Not binding to elastic IP as we are running in the DC");
            return;
        }
        // Needed for dev environment
        if (myZone == null) {
            s_logger.warn("The instance zone is null, so settting it to {}",
                    DEFAULT_ZONE);
            myZone = DEFAULT_ZONE;
        }

        String selectedEIP = getCandidateEIP(myInstanceId, myZone, myPublicIP);
        if (selectedEIP == null) {
            s_logger.info("No need to bind to EIP");
        } else {
            try {
                AmazonEC2 ec2Service = getEC2Service();
                AssociateAddressRequest request = new AssociateAddressRequest(
                        myInstanceId, selectedEIP);
                ec2Service.associateAddress(request);
                s_logger.info("\n\n\nAssociated " + myInstanceId
                        + " running in zone: " + myZone + " to elastic IP: "
                        + selectedEIP);
            } catch (Throwable t) {
                throw new RuntimeException("Failed to bind elastic IP: " + selectedEIP
                        + " to " + myInstanceId, t);
            }
        }
    }

    private AmazonEC2 getEC2Service() throws ConfigurationException,
            FileNotFoundException {
        AmazonEC2 ec2Service = new AmazonEC2Client(
                new BasicAWSCredentials(DynamicPropertyFactory.getInstance().getStringProperty(NETFLIX_DISCOVERY_EIP_AWS_ACCESS_ID, "").get()
                        , DynamicPropertyFactory.getInstance().getStringProperty(NETFLIX_DISCOVERY_EIP_AWS_SECRET_KEY, "").get()));
                       
        String region = ConfigurationManager.getDeploymentContext().getDeploymentRegion();
        if (region == null) {
            region = DEFAULT_REGION;
            s_logger.warn("Region not supplied. Defaulting to {}",
                    region);

        } else {
            region = region.trim().toLowerCase();
        }
        ec2Service.setEndpoint("ec2." + region + ".amazonaws.com");
        return ec2Service;
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
                s_logger.info("Dissociated the EIP {} from this instance",
                        myPublicIP);
            } catch (Throwable e) {
                throw new RuntimeException("Cannot dissociate address" + myPublicIP + "from this instance", e);
            }
        }

    }
    
    public String getCandidateEIP(String myInstanceId, String myZone,
            String myPublicIP) {
        boolean eipLookupFromDNS = DynamicPropertyFactory.getInstance().getBooleanProperty(
                PROP_EIP_LOOKUP_DNS, true).get();
        Collection<String> eipCandidates = (eipLookupFromDNS ? getEIPsForZoneFromDNS(myZone)
                : getEIPsforZoneFromProperties(myZone));
        if (eipCandidates == null || eipCandidates.size() == 0
                || myPublicIP == null) {
            return null;
        }
        Map<String, Long> eipMap = new HashMap<String, Long>();
        for (String eip : eipCandidates) {
            if (myPublicIP.equals(eip.trim())) {
                s_logger.info("We are already bound to EIP: " + eip);
                return null;
            }
            eipMap.put(eip.trim(), Long.valueOf(0));
        }

        Application app = DiscoveryManager.getInstance().getLookupService()
                .getApplication("DISCOVERY");

        if (app != null) {
            for (InstanceInfo i : app.getInstances()) {
                if (i.getDataCenterInfo().getName() == Name.Amazon) {
                    AmazonInfo info = (AmazonInfo) i.getDataCenterInfo();
                    String publicIP = info.get(MetaDataKey.publicIpv4);
                    if ((info.get(MetaDataKey.instanceId).equals(myInstanceId)) && (eipMap.containsKey(publicIP))) {
                        // This can happen if the server restarts and we haven't fully been dissociated with the previous
                        // shutdown
                        s_logger.info("The instance id %s is already bound to EIP {}. Hence returning that.", myInstanceId, publicIP);
                        return publicIP;
                    }
                    for (String eip : eipCandidates) {
                        // Pick the instance w/ the oldest regTS
                        if (eip.trim().equals(publicIP)) {
                            eipMap.put(eip.trim(), Long.valueOf(i
                                    .getLeaseInfo().getRegistrationTimestamp()));
                            s_logger.info("Considering EIP: " + publicIP);

                        }
                    }
                }

            }
        }
        // return the eip w/ the older reg ts
        long lastRegTS = Long.MAX_VALUE;
        String selectedEIP = null;
        for (Iterator<Map.Entry<String, Long>> iter = eipMap.entrySet()
                .iterator(); iter.hasNext();) {
            Map.Entry<String, Long> entry = iter.next();
            if (entry.getValue().longValue() < lastRegTS) {
                lastRegTS = entry.getValue().longValue();
                selectedEIP = entry.getKey();
            }
        }
        return selectedEIP;
    }

    private Collection<String> getEIPsForZoneFromDNS(String myZone) {
        String discoveryDomainName = DynamicPropertyFactory.getInstance().getStringProperty(
                PROP_DISCOVERY_DOMAIN_NAME, null).get();
        String zoneDnsName = myZone + "." + DiscoveryClient.getRegion() + "."
                + discoveryDomainName;
        return DiscoveryClient.getEC2DiscoveryUrlsFromZone(zoneDnsName,
                DiscoveryUrlType.A);
    }

    private List<String> getEIPsforZoneFromProperties(String myZone) {
        String eipPropertyValue = DynamicPropertyFactory.getInstance().getStringProperty(PROP_EIP + myZone, null)
                .get();
        s_logger.info("Candidate EIPs for zone " + myZone + ": "
                + eipPropertyValue);
        if (eipPropertyValue == null || eipPropertyValue.trim().equals("")) {
            return null;
        }

        String eipCandidates[] = eipPropertyValue.split(",");
        return Arrays.asList(eipCandidates);
    }

}

package com.netflix.discovery.util;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;

/**
 * A collection of utility functions that is useful to simplify operations on
 * {@link com.netflix.appinfo.ApplicationInfoManager}, {@link com.netflix.appinfo.InstanceInfo}
 * and {@link com.netflix.discovery.EurekaClient}
 *
 * @author David Liu
 */
public final class EurekaUtils {

    /**
     * return the privateIp address of the given InstanceInfo record. The record could be for the local server
     * or a remote server.
     *
     * @param instanceInfo
     * @return the private Ip (also known as localIpv4 in ec2)
     */
    public static String getPrivateIp(InstanceInfo instanceInfo) {
        String defaultPrivateIp = null;
        if (instanceInfo.getDataCenterInfo() instanceof AmazonInfo) {
            defaultPrivateIp = ((AmazonInfo) instanceInfo.getDataCenterInfo()).get(AmazonInfo.MetaDataKey.localIpv4);
        }

        if (isNullOrEmpty(defaultPrivateIp)) {
            // no other information, best effort
            defaultPrivateIp = instanceInfo.getIPAddr();
        }

        return defaultPrivateIp;
    }

    /**
     * check to see if the instanceInfo record is of a server that is deployed within EC2 (best effort check based
     * on assumptions of underlying id). This check could be for the local server or a remote server.
     *
     * @param instanceInfo
     * @return true if the record contains an EC2 style "i-*" id
     */
    public static boolean isInEc2(InstanceInfo instanceInfo) {
        if (instanceInfo.getDataCenterInfo() instanceof AmazonInfo) {
            String instanceId = ((AmazonInfo) instanceInfo.getDataCenterInfo()).getId();
            if (instanceId != null && instanceId.startsWith("i-")) {
                return true;
            }
        }
        return false;
    }

    /**
     * check to see if the instanceInfo record is of a server that is deployed within EC2 VPC (best effort check
     * based on assumption of existing vpcId). This check could be for the local server or a remote server.
     *
     * @param instanceInfo
     * @return true if the record contains a non null, non empty AWS vpcId
     */
    public static boolean isInVpc(InstanceInfo instanceInfo) {
        if (instanceInfo.getDataCenterInfo() instanceof AmazonInfo) {
            AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
            String vpcId = info.get(AmazonInfo.MetaDataKey.vpcId);
            return !isNullOrEmpty(vpcId);
        }

        return false;
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

}

package com.netflix.discovery.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Applications;

import static com.netflix.discovery.util.ApplicationFunctions.toApplicationMap;
import static com.netflix.discovery.util.ApplicationFunctions.toApplications;

/**
 * Test data generator.
 *
 * @author Tomasz Bak
 */
public class InstanceInfoGenerator {
    public static final int RENEW_INTERVAL = 5;

    private final int instanceCount;
    private final int applicationCount;

    private Iterator<InstanceInfo> currentIt;
    private Applications allApplications = new Applications();

    public InstanceInfoGenerator(int instanceCount, int applicationCount) {
        this.instanceCount = instanceCount;
        this.applicationCount = applicationCount;
    }

    public Applications takeDelta(int count) {
        if (currentIt == null) {
            currentIt = serviceIterator();
            allApplications = new Applications();
        }
        List<InstanceInfo> instanceBatch = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            InstanceInfo next = currentIt.next();
            next.setActionType(ActionType.ADDED);
            instanceBatch.add(next);
        }
        Applications nextBatch = toApplications(toApplicationMap(instanceBatch));
        allApplications = ApplicationFunctions.merge(allApplications, nextBatch);
        nextBatch.setAppsHashCode(allApplications.getAppsHashCode());

        return nextBatch;
    }

    public Iterator<InstanceInfo> serviceIterator() {
        return new Iterator<InstanceInfo>() {

            private int returned;
            private final int[] appInstanceIds = new int[applicationCount];
            private int currentApp;

            @Override
            public boolean hasNext() {
                return returned < instanceCount;
            }

            @Override
            public InstanceInfo next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("no more InstanceInfo elements");
                }
                InstanceInfo toReturn = generateInstanceInfo(currentApp, appInstanceIds[currentApp]);
                appInstanceIds[currentApp]++;
                currentApp = (currentApp + 1) % applicationCount;
                returned++;
                return toReturn;
            }

            @Override
            public void remove() {
                throw new IllegalStateException("method not supported");
            }
        };
    }

    public static InstanceInfo generateInstanceInfo(int appIndex, int appInstanceId) {
        String hostName = "instance" + appInstanceId + ".application" + appIndex + ".com";
        String publicIp = "20.0." + appIndex + '.' + appInstanceId;
        String privateIp = "192.168." + appIndex + '.' + appInstanceId;

        AmazonInfo dataCenterInfo = AmazonInfo.Builder.newBuilder()
                .addMetadata(MetaDataKey.accountId, "testAccountId")
                .addMetadata(MetaDataKey.amiId, String.format("ami-%04d%04d", appIndex, appInstanceId))
                .addMetadata(MetaDataKey.availabilityZone, "us-east1c")
                .addMetadata(MetaDataKey.instanceId, String.format("i-%04d%04d", appIndex, appInstanceId))
                .addMetadata(MetaDataKey.instanceType, "m2.xlarge")
                .addMetadata(MetaDataKey.localIpv4, privateIp)
                .addMetadata(MetaDataKey.publicHostname, hostName)
                .addMetadata(MetaDataKey.publicIpv4, publicIp)
                .build();

        String unsecureURL = "http://" + hostName + ":8080";
        String secureURL = "https://" + hostName + ":8081";

        LeaseInfo leaseInfo = LeaseInfo.Builder.newBuilder()
                .setDurationInSecs(RENEW_INTERVAL)
                .setRenewalIntervalInSecs(RENEW_INTERVAL)
                .build();

        return InstanceInfo.Builder.newBuilder()
                .setAppGroupName("AppGroup" + appIndex)
                .setAppName("App" + appIndex)
                .setASGName("ASG" + appIndex)
                .setHostName(hostName)
                .setIPAddr(publicIp)
                .setPort(8080)
                .setSecurePort(8081)
                .setHealthCheckUrls("/healthcheck", unsecureURL + "/healthcheck", secureURL + "/healthcheck")
                .setHomePageUrl("/homepage", unsecureURL + "/homepage")
                .setStatusPageUrl("/status", unsecureURL + "/status")
                .setLeaseInfo(leaseInfo)
                .setStatus(InstanceStatus.UP)
                .setVIPAddress(hostName + ":8080")
                .setSecureVIPAddress(hostName + ":8081")
                .setDataCenterInfo(dataCenterInfo)
                .enablePort(PortType.UNSECURE, true)
                .add("appKey" + appIndex, Integer.toString(appInstanceId))
                .build();
    }
}

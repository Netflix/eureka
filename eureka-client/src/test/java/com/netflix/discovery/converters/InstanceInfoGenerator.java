package com.netflix.discovery.converters;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * @author Tomasz Bak
 */
public class InstanceInfoGenerator {

    public static final int RENEW_INTERVAL = 30000;

    private final int instanceCount;
    private final int applicationCount;
    private final boolean withMetaData;

    public InstanceInfoGenerator(int instanceCount, int applicationCount, boolean withMetaData) {
        this.instanceCount = instanceCount;
        this.applicationCount = applicationCount;
        this.withMetaData = withMetaData;
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

    public Applications toApplications() {
        Map<String, Application> appsByName = new HashMap<String, Application>();
        Iterator<InstanceInfo> it = serviceIterator();
        while (it.hasNext()) {
            InstanceInfo instanceInfo = it.next();
            Application instanceApp = appsByName.get(instanceInfo.getAppName());
            if (instanceApp == null) {
                instanceApp = new Application(instanceInfo.getAppName());
                appsByName.put(instanceInfo.getAppName(), instanceApp);
            }
            instanceApp.addInstance(instanceInfo);
        }

        // Do not pass application list to the constructor, as it does not initialize properly Applications
        // data structure.
        Applications applications = new Applications();
        for (Application app : appsByName.values()) {
            applications.addApplication(app);
        }

        applications.setAppsHashCode(applications.getReconcileHashCode());

        return applications;
    }

    public static InstanceInfo takeOne() {
        return new InstanceInfoGenerator(1, 1, true).serviceIterator().next();
    }

    private InstanceInfo generateInstanceInfo(int appIndex, int appInstanceId) {
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

        Builder builder = InstanceInfo.Builder.newBuilder()
                .setAppGroupName("AppGroup" + appIndex)
                .setAppName("App" + appIndex)
                .setASGName("ASG" + appIndex)
                .setHostName(hostName)
                .setIPAddr(publicIp)
                .setPort(8080)
                .setSecurePort(8081)
                .enablePort(PortType.SECURE, true)
                .setHealthCheckUrls("/healthcheck", unsecureURL + "/healthcheck", secureURL + "/healthcheck")
                .setHomePageUrl("/homepage", unsecureURL + "/homepage")
                .setStatusPageUrl("/status", unsecureURL + "/status")
                .setLeaseInfo(leaseInfo)
                .setStatus(InstanceStatus.UP)
                .setVIPAddress(hostName + ":8080")
                .setSecureVIPAddress(hostName + ":8081")
                .setDataCenterInfo(dataCenterInfo)
                .enablePort(PortType.UNSECURE, true);
        if (withMetaData) {
            builder.add("appKey" + appIndex, Integer.toString(appInstanceId));
        }
        return builder.build();
    }
}

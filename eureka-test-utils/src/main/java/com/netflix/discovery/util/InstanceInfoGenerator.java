package com.netflix.discovery.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

import static com.netflix.discovery.util.EurekaEntityFunctions.mergeApplications;
import static com.netflix.discovery.util.EurekaEntityFunctions.toApplicationMap;

/**
 * Test data generator.
 *
 * @author Tomasz Bak
 */
public class InstanceInfoGenerator {
    public static final int RENEW_INTERVAL = 5;

    private final int instanceCount;
    private final String[] appNames;
    private final String zone;
    private final boolean taggedId;

    private Iterator<InstanceInfo> currentIt;
    private Applications allApplications = new Applications();
    private final boolean withMetaData;
    private final boolean includeAsg;
    private final boolean useInstanceId;

    InstanceInfoGenerator(InstanceInfoGeneratorBuilder builder) {
        this.instanceCount = builder.instanceCount;
        this.appNames = builder.appNames;
        this.zone = builder.zone == null ? "us-east-1c" : builder.zone;
        this.taggedId = builder.taggedId;
        this.withMetaData = builder.includeMetaData;
        this.includeAsg = builder.includeAsg;
        this.useInstanceId = builder.useInstanceId;
    }

    public Applications takeDelta(int count) {
        if (currentIt == null) {
            currentIt = serviceIterator();
            allApplications = new Applications();
        }
        List<InstanceInfo> instanceBatch = new ArrayList<InstanceInfo>();
        for (int i = 0; i < count; i++) {
            InstanceInfo next = currentIt.next();
            next.setActionType(ActionType.ADDED);
            instanceBatch.add(next);
        }
        Applications nextBatch = EurekaEntityFunctions.toApplications(toApplicationMap(instanceBatch));
        allApplications = mergeApplications(allApplications, nextBatch);
        nextBatch.setAppsHashCode(allApplications.getAppsHashCode());

        return nextBatch;
    }

    public Iterator<InstanceInfo> serviceIterator() {
        return new Iterator<InstanceInfo>() {

            private int returned;
            private final int[] appInstanceIds = new int[appNames.length];
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
                InstanceInfo toReturn = generateInstanceInfo(currentApp, appInstanceIds[currentApp], useInstanceId, ActionType.ADDED);
                appInstanceIds[currentApp]++;
                currentApp = (currentApp + 1) % appNames.length;
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
        Map<String, Application> appsByName = new HashMap<>();
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

        applications.shuffleInstances(false);
        applications.setAppsHashCode(applications.getReconcileHashCode());
        applications.setVersion(1L);

        return applications;
    }

    public List<InstanceInfo> toInstanceList() {
        List<InstanceInfo> result = new ArrayList<>(instanceCount);
        Iterator<InstanceInfo> it = serviceIterator();
        while (it.hasNext()) {
            InstanceInfo instanceInfo = it.next();
            result.add(instanceInfo);
        }
        return result;
    }

    public InstanceInfo first() {
        return take(0);
    }

    public InstanceInfo take(int idx) {
        return toInstanceList().get(idx);
    }

    public static InstanceInfo takeOne() {
        return newBuilder(1, 1).withMetaData(true).build().serviceIterator().next();
    }

    public static InstanceInfoGeneratorBuilder newBuilder(int instanceCount, int applicationCount) {
        return new InstanceInfoGeneratorBuilder(instanceCount, applicationCount);
    }

    public static InstanceInfoGeneratorBuilder newBuilder(int instanceCount, String... appNames) {
        return new InstanceInfoGeneratorBuilder(instanceCount, appNames);
    }

    public Applications takeDeltaForDelete(boolean useInstanceId, int instanceCount) {
        List<InstanceInfo> instanceInfoList = new ArrayList<>();
        for (int i = 0; i < instanceCount; i ++) {
            instanceInfoList.add(this.generateInstanceInfo(i, i, useInstanceId, ActionType.DELETED));
        }
        Applications delete = EurekaEntityFunctions.toApplications(toApplicationMap(instanceInfoList));
        allApplications = mergeApplications(allApplications, delete);
        delete.setAppsHashCode(allApplications.getAppsHashCode());
        return delete;
    }

    // useInstanceId to false to generate older InstanceInfo types that does not use instanceId field for instance id.
    private InstanceInfo generateInstanceInfo(int appIndex, int appInstanceId, boolean useInstanceId, ActionType actionType) {
        String appName = appNames[appIndex];
        String hostName = "instance" + appInstanceId + '.' + appName + ".com";
        String privateHostname = "ip-10.0" + appIndex + "." + appInstanceId + ".compute.internal";
        String publicIp = "20.0." + appIndex + '.' + appInstanceId;
        String privateIp = "192.168." + appIndex + '.' + appInstanceId;

        String instanceId = String.format("i-%04d%04d", appIndex, appInstanceId);
        if (taggedId) {
            instanceId = instanceId + '_' + appName;
        }

        AmazonInfo dataCenterInfo = AmazonInfo.Builder.newBuilder()
                .addMetadata(MetaDataKey.accountId, "testAccountId")
                .addMetadata(MetaDataKey.amiId, String.format("ami-%04d%04d", appIndex, appInstanceId))
                .addMetadata(MetaDataKey.availabilityZone, zone)
                .addMetadata(MetaDataKey.instanceId, instanceId)
                .addMetadata(MetaDataKey.instanceType, "m2.xlarge")
                .addMetadata(MetaDataKey.localHostname, privateHostname)
                .addMetadata(MetaDataKey.localIpv4, privateIp)
                .addMetadata(MetaDataKey.publicHostname, hostName)
                .addMetadata(MetaDataKey.publicIpv4, publicIp)
                .build();

        String unsecureURL = "http://" + hostName + ":8080";
        String secureURL = "https://" + hostName + ":8081";

        long now = System.currentTimeMillis();
        LeaseInfo leaseInfo = LeaseInfo.Builder.newBuilder()
                .setDurationInSecs(3 * RENEW_INTERVAL)
                .setRenewalIntervalInSecs(RENEW_INTERVAL)
                .setServiceUpTimestamp(now - RENEW_INTERVAL)
                .setRegistrationTimestamp(now)
                .setEvictionTimestamp(now + 3 * RENEW_INTERVAL)
                .setRenewalTimestamp(now + RENEW_INTERVAL)
                .build();

        Builder builder = useInstanceId
                ? InstanceInfo.Builder.newBuilder().setInstanceId(instanceId)
                : InstanceInfo.Builder.newBuilder();

        builder
                .setActionType(actionType)
                .setAppGroupName(appName + "Group")
                .setAppName(appName)
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
                .setVIPAddress(appName + ":8080")
                .setSecureVIPAddress(appName + ":8081")
                .setDataCenterInfo(dataCenterInfo)
                .setLastUpdatedTimestamp(System.currentTimeMillis() - 100)
                .setLastDirtyTimestamp(System.currentTimeMillis() - 100)
                .setIsCoordinatingDiscoveryServer(true)
                .enablePort(PortType.UNSECURE, true);
        if (includeAsg) {
            builder.setASGName(appName + "ASG");
        }
        if (withMetaData) {
            builder.add("appKey" + appIndex, Integer.toString(appInstanceId));
        }
        return builder.build();
    }

    public static class InstanceInfoGeneratorBuilder {

        private final int instanceCount;

        private String[] appNames;

        private boolean includeMetaData;
        private boolean includeAsg = true;
        private String zone;
        private boolean taggedId;
        private boolean useInstanceId = true;

        public InstanceInfoGeneratorBuilder(int instanceCount, int applicationCount) {
            this.instanceCount = instanceCount;
            String[] appNames = new String[applicationCount];
            for (int i = 0; i < appNames.length; i++) {
                appNames[i] = "application" + i;
            }
            this.appNames = appNames;
        }

        public InstanceInfoGeneratorBuilder(int instanceCount, String... appNames) {
            this.instanceCount = instanceCount;
            this.appNames = appNames;
        }

        public InstanceInfoGeneratorBuilder withZone(String zone) {
            this.zone = zone;
            return this;
        }

        public InstanceInfoGeneratorBuilder withTaggedId(boolean taggedId) {
            this.taggedId = taggedId;
            return this;
        }

        public InstanceInfoGeneratorBuilder withMetaData(boolean includeMetaData) {
            this.includeMetaData = includeMetaData;
            return this;
        }

        public InstanceInfoGeneratorBuilder withAsg(boolean includeAsg) {
            this.includeAsg = includeAsg;
            return this;
        }

        public InstanceInfoGeneratorBuilder withUseInstanceId(boolean useInstanceId) {
            this.useInstanceId = useInstanceId;
            return this;
        }

        public InstanceInfoGenerator build() {
            return new InstanceInfoGenerator(this);
        }
    }
}

package com.netflix.eureka2.eureka1.rest.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.datacenter.BasicDataCenterInfo;
import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.registry.selector.AddressSelector;

import static com.netflix.eureka2.utils.ExtCollections.asSet;

/**
 * Map Eureka 2.x domain model to Eureka 1.x abstractions.
 *
 * @author Tomasz Bak
 */
public final class Eureka1ModelConverters {

    private static final AddressSelector ADDRESS_SELECTOR = AddressSelector.selectBy()
            .protocolType(ProtocolType.IPv4).publicIp(true).or().any();

    private static final MyEureka1xDataCenterInfo MY_OWN_DATA_CENTER_INFO = new MyEureka1xDataCenterInfo();

    private static final Comparator<com.netflix.appinfo.InstanceInfo> V1_INSTANCE_IDENTITY_COMPARATOR =
            new Comparator<com.netflix.appinfo.InstanceInfo>() {
                @Override
                public int compare(com.netflix.appinfo.InstanceInfo o1, com.netflix.appinfo.InstanceInfo o2) {
                    return o1.getId().compareTo(o2.getId());
                }
            };

    private Eureka1ModelConverters() {
    }

    public static Comparator<com.netflix.appinfo.InstanceInfo> v1InstanceIdentityComparator() {
        return V1_INSTANCE_IDENTITY_COMPARATOR;
    }

    public static InstanceStatus toEureka1xStatus(Status v2Status) {
        switch (v2Status) {
            case DOWN:
                return InstanceStatus.DOWN;
            case OUT_OF_SERVICE:
                return InstanceStatus.OUT_OF_SERVICE;
            case STARTING:
                return InstanceStatus.STARTING;
            case UNKNOWN:
                return InstanceStatus.UNKNOWN;
            case UP:
                return InstanceStatus.UP;
        }
        throw new IllegalStateException("Unexpected Eureka 2.x status " + v2Status);
    }

    public static com.netflix.appinfo.DataCenterInfo toEureka1xDataCenterInfo(DataCenterInfo v2DataCenterInfo) {
        if (v2DataCenterInfo instanceof AwsDataCenterInfo) {
            return toEureka1xDataCenterInfo((AwsDataCenterInfo) v2DataCenterInfo);
        }
        return MY_OWN_DATA_CENTER_INFO;
    }

    public static AmazonInfo toEureka1xDataCenterInfo(AwsDataCenterInfo v2DataCenterInfo) {
        AmazonInfo.Builder builder = AmazonInfo.Builder.newBuilder();
        builder.addMetadata(MetaDataKey.amiId, v2DataCenterInfo.getAmiId());
        builder.addMetadata(MetaDataKey.availabilityZone, v2DataCenterInfo.getZone());
        builder.addMetadata(MetaDataKey.instanceId, v2DataCenterInfo.getInstanceId());
        builder.addMetadata(MetaDataKey.instanceType, v2DataCenterInfo.getInstanceType());
        builder.addMetadata(MetaDataKey.localIpv4, v2DataCenterInfo.getPrivateAddress().getIpAddress());
        builder.addMetadata(MetaDataKey.publicHostname, v2DataCenterInfo.getPublicAddress().getHostName());
        builder.addMetadata(MetaDataKey.publicIpv4, v2DataCenterInfo.getPublicAddress().getIpAddress());
        return builder.build();
    }

    public static com.netflix.appinfo.InstanceInfo toEureka1xInstanceInfo(InstanceInfo v2InstanceInfo, ActionType actionType) {
        Builder builder = Builder.newBuilder();

        builder.setAppGroupName(v2InstanceInfo.getAppGroup());
        builder.setAppName(v2InstanceInfo.getApp());
        builder.setASGName(v2InstanceInfo.getAsg());
        builder.setVIPAddress(v2InstanceInfo.getVipAddress());
        builder.setSecureVIPAddress(v2InstanceInfo.getSecureVipAddress());
        builder.setStatus(toEureka1xStatus(v2InstanceInfo.getStatus()));

        // Network addresses
        NetworkAddress address = ADDRESS_SELECTOR.returnAddress(v2InstanceInfo.getDataCenterInfo().getAddresses());
        builder.setHostName(address.getHostName() == null ? address.getIpAddress() : address.getHostName());
        builder.setIPAddr(address.getIpAddress());

        // Home/status URLs
        if (v2InstanceInfo.getHomePageUrl() != null) {
            builder.setHomePageUrl(relativeUrlOf(v2InstanceInfo.getHomePageUrl()), v2InstanceInfo.getHomePageUrl());
        }
        if (v2InstanceInfo.getStatusPageUrl() != null) {
            builder.setStatusPageUrl(relativeUrlOf(v2InstanceInfo.getStatusPageUrl()), v2InstanceInfo.getStatusPageUrl());
        }

        // Map healthcheck URLs
        if (v2InstanceInfo.getHealthCheckUrls() != null) {
            String explicitHealthCheckUrl = null;
            String explicitSecureHealthCheckUrl = null;

            for (String url : v2InstanceInfo.getHealthCheckUrls()) {
                if (url.startsWith("https")) {
                    if (explicitSecureHealthCheckUrl == null) {
                        explicitSecureHealthCheckUrl = url;
                    }
                } else if (explicitHealthCheckUrl == null) {
                    explicitHealthCheckUrl = url;
                }
            }
            String relativeHealthCheckUrl = explicitHealthCheckUrl != null ?
                    relativeUrlOf(explicitHealthCheckUrl) : relativeUrlOf(explicitSecureHealthCheckUrl);

            builder.setHealthCheckUrls(relativeHealthCheckUrl, explicitHealthCheckUrl, explicitSecureHealthCheckUrl);
        }

        // Lease
        builder.setLeaseInfo(createDummyLeaseInfo());

        // Map data center
        builder.setDataCenterInfo(toEureka1xDataCenterInfo(v2InstanceInfo.getDataCenterInfo()));

        // Port mapping
        for (ServicePort servicePort : v2InstanceInfo.getPorts()) {
            if (servicePort.isSecure()) {
                builder.enablePort(PortType.SECURE, true);
                builder.setSecurePort(servicePort.getPort());
            } else {
                builder.enablePort(PortType.UNSECURE, true);
                builder.setPort(servicePort.getPort());
            }
        }

        // Meta data
        if (v2InstanceInfo.getMetaData() != null) {
            for (Entry<String, String> entry : v2InstanceInfo.getMetaData().entrySet()) {
                builder.add(entry.getKey(), entry.getValue());
            }
        }

        com.netflix.appinfo.InstanceInfo v1InstanceInfo = builder.build();
        v1InstanceInfo.setActionType(actionType);
        return v1InstanceInfo;
    }

    public static com.netflix.appinfo.InstanceInfo toEureka1xInstanceInfo(InstanceInfo v2InstanceInfo) {
        return toEureka1xInstanceInfo(v2InstanceInfo, null);
    }

    public static List<com.netflix.appinfo.InstanceInfo> toEureka1xInstanceInfos(Collection<InstanceInfo> v2Instances) {
        List<com.netflix.appinfo.InstanceInfo> v1Instances = new ArrayList<>(v2Instances.size());
        for (InstanceInfo v2Instance : v2Instances) {
            v1Instances.add(toEureka1xInstanceInfo(v2Instance));
        }
        return v1Instances;
    }

    public static LeaseInfo createDummyLeaseInfo() {
        LeaseInfo.Builder leaseBuilder = LeaseInfo.Builder.newBuilder();
        leaseBuilder.setDurationInSecs(30);
        long now = System.currentTimeMillis();
        leaseBuilder.setRegistrationTimestamp(now);
        leaseBuilder.setRenewalIntervalInSecs(30);
        leaseBuilder.setRenewalTimestamp(now);

        return leaseBuilder.build();
    }

    public static Application toEureka1xApplication(String applicationName, Collection<InstanceInfo> applicationV2Instances) {
        Application application = new Application(applicationName);
        for (InstanceInfo v2Instance : applicationV2Instances) {
            application.addInstance(toEureka1xInstanceInfo(v2Instance));
        }
        return application;
    }

    /**
     * Map Eureka2 registry to Eureka's 1.x {@link Applications} data structure. It is potentially expensive
     * operation as each registry item is mapped to 1.x model, and associated with its corresponding {@link Application}
     * instance.
     */
    public static Applications toEureka1xApplicationsFromV2Collection(Collection<InstanceInfo> v2Instances) {
        return toEureka1xApplications(toEureka1xInstanceInfos(v2Instances));
    }

    public static Applications toEureka1xApplications(Collection<com.netflix.appinfo.InstanceInfo> v1Instances) {
        Map<String, Application> applicationMap = new HashMap<>();
        for (com.netflix.appinfo.InstanceInfo v1Instance : v1Instances) {
            String appName = v1Instance.getAppName().toUpperCase(Locale.ROOT);
            Application application = applicationMap.get(appName);
            if (application == null) {
                application = new Application(appName);
                applicationMap.put(appName, application);
            }

            application.addInstance(v1Instance);
        }

        // Do not pass application list to the constructor, as it does not initialize properly Applications
        // data structure.
        Applications applications = new Applications();
        for (Application app : applicationMap.values()) {
            applications.addApplication(app);
        }

        applications.setAppsHashCode(applications.getReconcileHashCode());

        return applications;
    }

    public static InstanceInfo.Status tuEureka2xStatus(InstanceStatus v1Status) {
        switch (v1Status) {
            case DOWN:
                return InstanceInfo.Status.DOWN;
            case OUT_OF_SERVICE:
                return InstanceInfo.Status.OUT_OF_SERVICE;
            case STARTING:
                return InstanceInfo.Status.STARTING;
            case UNKNOWN:
                return InstanceInfo.Status.UNKNOWN;
            case UP:
                return InstanceInfo.Status.UP;
        }
        throw new IllegalStateException("Unexpected Eureka 1.x status " + v1Status);
    }

    public static DataCenterInfo toEureka2xDataCenterInfo(com.netflix.appinfo.DataCenterInfo v1DataCenterInfo) {
        DataCenterInfo.DataCenterInfoBuilder<?> builder;

        if (v1DataCenterInfo instanceof AmazonInfo) {
            AmazonInfo v1Info = (AmazonInfo) v1DataCenterInfo;

            builder = new AwsDataCenterInfo.Builder()
                    .withZone(v1Info.get(AmazonInfo.MetaDataKey.availabilityZone))
                    .withAmiId(v1Info.get(AmazonInfo.MetaDataKey.amiId))
                    .withInstanceId(v1Info.get(AmazonInfo.MetaDataKey.instanceId))
                    .withInstanceType(v1Info.get(AmazonInfo.MetaDataKey.instanceType))
                    .withPrivateIPv4(v1Info.get(AmazonInfo.MetaDataKey.localIpv4))
                    .withPublicIPv4(v1Info.get(AmazonInfo.MetaDataKey.publicIpv4))
                    .withPublicHostName(v1Info.get(AmazonInfo.MetaDataKey.publicHostname));
        } else {
            builder = new BasicDataCenterInfo.BasicDataCenterInfoBuilder()
                    .withName(v1DataCenterInfo.getName().name());
        }
        return builder.build();
    }

    public static InstanceInfo toEureka2xInstanceInfo(com.netflix.appinfo.InstanceInfo v1InstanceInfo) {
        InstanceInfo.Builder builder = new InstanceInfo.Builder()
                .withId(v1InstanceInfo.getAppName() + '_' + v1InstanceInfo.getId())  // instanceId is not unique for v1Data
                .withAppGroup(v1InstanceInfo.getAppGroupName())
                .withApp(v1InstanceInfo.getAppName())
                .withAsg(v1InstanceInfo.getASGName())
                .withVipAddress(v1InstanceInfo.getVIPAddress())
                .withSecureVipAddress(v1InstanceInfo.getSecureVipAddress())
                .withPorts(asSet(new ServicePort(v1InstanceInfo.getPort(), false), new ServicePort(v1InstanceInfo.getSecurePort(), true)))
                .withStatus(tuEureka2xStatus(v1InstanceInfo.getStatus()))
                .withHomePageUrl(v1InstanceInfo.getHomePageUrl())
                .withStatusPageUrl(v1InstanceInfo.getStatusPageUrl())
                .withHealthCheckUrls(new HashSet<>(v1InstanceInfo.getHealthCheckUrls()))
                .withMetaData(v1InstanceInfo.getMetadata())
                .withDataCenterInfo(toEureka2xDataCenterInfo(v1InstanceInfo.getDataCenterInfo()));
        return builder.build();
    }

    private static String relativeUrlOf(String absoluteUrl) {
        if (absoluteUrl == null) {
            return null;
        }
        try {
            URI uri = new URI(absoluteUrl);
            return uri.getPath();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private static class MyEureka1xDataCenterInfo implements com.netflix.appinfo.DataCenterInfo {
        @Override
        public Name getName() {
            return Name.MyOwn;
        }
    }
}

package com.netflix.eureka2.server.bridge;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.datacenter.BasicDataCenterInfo;

import java.util.Collections;
import java.util.HashSet;

import static com.netflix.eureka2.registry.instance.NetworkAddress.NetworkAddressBuilder.aNetworkAddress;

/**
 * @author David Liu
 */
public class InstanceInfoConverterImpl implements InstanceInfoConverter {

    @Override
    public InstanceInfo fromV1(com.netflix.appinfo.InstanceInfo v1Info) {
        InstanceInfo.Builder builder = new InstanceInfo.Builder()
                .withId(v1Info.getAppName() + "_" + v1Info.getId())  // instanceId is not unique for v1Data
                .withAppGroup(v1Info.getAppGroupName())
                .withApp(v1Info.getAppName())
                .withAsg(v1Info.getASGName())
                .withVipAddress(v1Info.getVIPAddress())
                .withSecureVipAddress(v1Info.getSecureVipAddress())
                .withPorts(toSet(new ServicePort(v1Info.getPort(), false), new ServicePort(v1Info.getSecurePort(), true)))
                .withStatus(fromV1(v1Info.getStatus()))
                .withHomePageUrl(v1Info.getHomePageUrl())
                .withStatusPageUrl(v1Info.getStatusPageUrl())
                .withHealthCheckUrls(new HashSet<>(v1Info.getHealthCheckUrls()))
                .withMetaData(v1Info.getMetadata())
                .withDataCenterInfo(dataCenterFromV1(v1Info));

        return builder.build();
    }

    @Override
    public InstanceInfo.Status fromV1(com.netflix.appinfo.InstanceInfo.InstanceStatus v1Status) {
        return InstanceInfo.Status.toEnum(v1Status.name());
    }

    @Override
    public DataCenterInfo dataCenterFromV1(com.netflix.appinfo.InstanceInfo v1Info) {
        DataCenterInfo.DataCenterInfoBuilder builder;

        com.netflix.appinfo.DataCenterInfo v1DataCenterInfo = v1Info.getDataCenterInfo();
        if (v1DataCenterInfo instanceof AmazonInfo) {
            AmazonInfo v1AmazonInfo = (AmazonInfo) v1DataCenterInfo;

            builder = new AwsDataCenterInfo.Builder()
                    .withZone(v1AmazonInfo.get(AmazonInfo.MetaDataKey.availabilityZone))
                    .withAmiId(v1AmazonInfo.get(AmazonInfo.MetaDataKey.amiId))
                    .withInstanceId(v1AmazonInfo.get(AmazonInfo.MetaDataKey.instanceId))
                    .withInstanceType(v1AmazonInfo.get(AmazonInfo.MetaDataKey.instanceType))
                    .withPrivateIPv4(v1AmazonInfo.get(AmazonInfo.MetaDataKey.localIpv4))
                    .withPublicIPv4(v1AmazonInfo.get(AmazonInfo.MetaDataKey.publicIpv4))
                    .withPublicHostName(v1AmazonInfo.get(AmazonInfo.MetaDataKey.publicHostname));
        } else {
            NetworkAddress address = aNetworkAddress()
                    .withIpAddress(v1Info.getIPAddr())
                    .withHostName(v1Info.getHostName())
                    .withLabel(NetworkAddress.PUBLIC_ADDRESS)  // best guess
                    .withProtocolType(NetworkAddress.ProtocolType.IPv4)  // best guess
                    .build();

            builder = new BasicDataCenterInfo.Builder<>()
                    .withName(v1DataCenterInfo.getName().name())
                    .withAddresses(address);
        }

        return builder.build();
    }

    @SafeVarargs
    private static <E> HashSet<E> toSet(E... elements) {
        HashSet<E> set = new HashSet<>();
        Collections.addAll(set, elements);
        return set;
    }
}

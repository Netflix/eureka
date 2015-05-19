package com.netflix.appinfo.providers;

import java.util.Map;

import javax.inject.Inject;

import com.netflix.governator.guice.lazy.LazySingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provider;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;

/**
 * InstanceInfo provider that constructs the InstanceInfo this this instance using
 * EurekaInstanceConfig.
 *
 * @author elandau
 *
 */
@LazySingleton
public class EurekaConfigBasedInstanceInfoProvider implements Provider<InstanceInfo> {
    private static final Logger LOG = LoggerFactory.getLogger(EurekaConfigBasedInstanceInfoProvider.class);

    private final EurekaInstanceConfig config;

    private InstanceInfo instanceInfo;
    
    @Inject
    public EurekaConfigBasedInstanceInfoProvider(EurekaInstanceConfig config) {
        this.config = config;
    }

    @Override
    public synchronized InstanceInfo get() {
        if (instanceInfo == null) {
            // Build the lease information to be passed to the server based
            // on config
            LeaseInfo.Builder leaseInfoBuilder = LeaseInfo.Builder
            .newBuilder()
            .setRenewalIntervalInSecs(
                    config.getLeaseRenewalIntervalInSeconds())
                    .setDurationInSecs(
                            config.getLeaseExpirationDurationInSeconds());
    
            // Builder the instance information to be registered with eureka
            // server
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
    
            builder.setNamespace(config.getNamespace())
                .setAppName(config.getAppname())
                .setAppGroupName(config.getAppGroupName())
                .setDataCenterInfo(config.getDataCenterInfo())
                .setIPAddr(config.getIpAddress())
                .setHostName(config.getHostName(false))
                .setPort(config.getNonSecurePort())
                .enablePort(PortType.UNSECURE,
                        config.isNonSecurePortEnabled())
                .setSecurePort(config.getSecurePort())
                .enablePort(PortType.SECURE, config.getSecurePortEnabled())
                .setVIPAddress(config.getVirtualHostName())
                .setSecureVIPAddress(config.getSecureVirtualHostName())
                .setHomePageUrl(config.getHomePageUrlPath(),
                                config.getHomePageUrl())
                .setStatusPageUrl(config.getStatusPageUrlPath(),
                                  config.getStatusPageUrl())
                .setHealthCheckUrls(config.getHealthCheckUrlPath(),
                                    config.getHealthCheckUrl(),
                                    config.getSecureHealthCheckUrl())
                .setASGName(config.getASGName());
    
            // Start off with the STARTING state to avoid traffic
            if (!config.isInstanceEnabledOnit()) {
                InstanceStatus initialStatus = InstanceStatus.STARTING;
                LOG.info("Setting initial instance status as: " + initialStatus);
                builder.setStatus(initialStatus);
            } else {
                LOG.info("Setting initial instance status as: " + InstanceStatus.UP
                        + ". This may be too early for the instance to advertise itself as available. "
                        + "You would instead want to control this via a healthcheck handler.");
            }
    
            // Add any user-specific metadata information
            for (Map.Entry<String, String> mapEntry : config.getMetadataMap()
                    .entrySet()) {
                String key = mapEntry.getKey();
                String value = mapEntry.getValue();
                builder.add(key, value);
            }
    
            instanceInfo = builder.build();
            instanceInfo.setLeaseInfo(leaseInfoBuilder.build());
        }
        return instanceInfo;
    }

}

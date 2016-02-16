package com.netflix.discovery.shared.resolver.aws;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.ResolverUtils;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class ApplicationsResolver implements ClusterResolver<AwsEndpoint> {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationsResolver.class);

    private final EurekaClientConfig clientConfig;
    private final EurekaTransportConfig transportConfig;
    private final ApplicationsSource applicationsSource;
    private final String vipAddress;

    @Deprecated
    public ApplicationsResolver(EurekaClientConfig clientConfig,
                                EurekaTransportConfig transportConfig,
                                ApplicationsSource applicationsSource) {
        this(clientConfig, transportConfig, applicationsSource, transportConfig.getReadClusterVip());
    }

    public ApplicationsResolver(EurekaClientConfig clientConfig,
                                EurekaTransportConfig transportConfig,
                                ApplicationsSource applicationsSource,
                                String vipAddress) {
        this.clientConfig = clientConfig;
        this.transportConfig = transportConfig;
        this.applicationsSource = applicationsSource;
        this.vipAddress = vipAddress;
    }

    @Override
    public String getRegion() {
        return null;
    }

    @Override
    public List<AwsEndpoint> getClusterEndpoints() {
        List<AwsEndpoint> result = new ArrayList<>();

        Applications applications = applicationsSource.getApplications(
                transportConfig.getApplicationsResolverDataStalenessThresholdSeconds(), TimeUnit.SECONDS);

        if (applications != null && vipAddress != null) {
            List<InstanceInfo> validInstanceInfos = applications.getInstancesByVirtualHostName(vipAddress);
            for (InstanceInfo instanceInfo : validInstanceInfos) {
                if (instanceInfo.getStatus() == InstanceInfo.InstanceStatus.UP) {
                    AwsEndpoint endpoint = ResolverUtils.instanceInfoToEndpoint(clientConfig, transportConfig, instanceInfo);
                    if (endpoint != null) {
                        result.add(endpoint);
                    }
                }
            }
        }

        logger.debug("Retrieved endpoint list {}", result);
        return result;

    }

    public interface ApplicationsSource {
        /**
         * @return the known set of Applications, or null if the data is beyond the stalenss threshold
         */
        Applications getApplications(int stalenessThreshold, TimeUnit timeUnit);
    }
}

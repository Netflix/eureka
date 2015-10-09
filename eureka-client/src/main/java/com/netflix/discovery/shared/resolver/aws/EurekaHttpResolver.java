package com.netflix.discovery.shared.resolver.aws;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.resolver.ClosableResolver;
import com.netflix.discovery.shared.resolver.ClusterResolver;
import com.netflix.discovery.shared.resolver.EurekaEndpoint;
import com.netflix.discovery.shared.resolver.ResolverUtils;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.decorator.RetryableEurekaHttpClient;
import com.netflix.discovery.shared.transport.decorator.ServerStatusEvaluators;
import com.netflix.discovery.shared.transport.jersey.JerseyEurekaHttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author David Liu
 */
public class EurekaHttpResolver implements ClosableResolver<AwsEndpoint> {
    private static final Logger logger = LoggerFactory.getLogger(EurekaHttpResolver.class);

    private final EurekaClientConfig clientConfig;
    private final InstanceInfo myInstanceInfo;
    private final String appName;
    private final EurekaHttpClientFactory clientFactory;

    public EurekaHttpResolver(EurekaClientConfig clientConfig,
                              InstanceInfo myInstanceInfo,
                              ClusterResolver<EurekaEndpoint> bootstrapResolver,
                              String appName) {
        this.clientConfig = clientConfig;
        this.myInstanceInfo = myInstanceInfo;
        this.appName = appName;
        this.clientFactory = RetryableEurekaHttpClient.createFactory(
                bootstrapResolver,
                JerseyEurekaHttpClientFactory.create(clientConfig, myInstanceInfo, resolverIdentity),
                ServerStatusEvaluators.httpSuccessEvaluator()
        );
    }

    @Override
    public void shutdown() {
        if (clientFactory != null) {
            clientFactory.shutdown();
        }
    }

    @Override
    public String getRegion() {
        return clientConfig.getRegion();
    }

    @Override
    public List<AwsEndpoint> getClusterEndpoints() {
        List<AwsEndpoint> result = new ArrayList<>();

        EurekaHttpClient client = null;
        try {
            client = clientFactory.newClient();
            EurekaHttpResponse<Application> response = client.getApplication(appName);
            if (validResponse(response)) {
                Application application = response.getEntity();
                if (application != null) {
                    List<InstanceInfo> validInstanceInfos = application.getInstances();
                    for (InstanceInfo instanceInfo : validInstanceInfos) {
                        if (instanceInfo.getStatus() == InstanceInfo.InstanceStatus.UP) {
                            result.add(ResolverUtils.instanceInfoToEndpoint(clientConfig, instanceInfo));
                        }
                    }
                    logger.debug("Retrieved endpoint list {}", result);
                    return result;
                }
            }
        } catch (Exception e) {
            logger.error("Error contacting server for endpoints with appName:{}", appName, e);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }

        logger.info("Returning empty endpoint list");
        return Collections.emptyList();
    }

    private <T> boolean validResponse(EurekaHttpResponse<T> response) {
        if (response == null) {
            return false;
        }

        int responseCode = response.getStatusCode();
        return responseCode >= 200 && responseCode < 300;
    }


    private final AbstractEurekaIdentity resolverIdentity = new AbstractEurekaIdentity() {
        @Override
        public String getName() {
            return EurekaHttpResolver.class.getSimpleName();
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Nullable
        @Override
        public String getId() {
            return myInstanceInfo.getId();
        }

        @Override
        public String toString() {
            return getName() + "-" + getVersion() + "-" + getId();
        }
    };
}

package netflix.adminresources.resources;

import com.google.inject.Singleton;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;

@Singleton
public class EurekaClientProviderImpl implements EurekaClientProvider {
    private final String CONFIG_DISCOVERY_PORT = "eureka.client.discovery-endpoint.port";
    private final String CONFIG_DISCOVERY_DNS = "eureka.client.discovery-endpoint.dns";
    private int port = ConfigurationManager.getConfigInstance().getInt(CONFIG_DISCOVERY_PORT, 12001);
    private String discoveryDNS = ConfigurationManager.getConfigInstance().getString(CONFIG_DISCOVERY_DNS, "localhost");

    @Override
    public EurekaClient get() {
        return Eureka.newClientBuilder(ServerResolvers.forDnsName(discoveryDNS, port)).build();
    }

}

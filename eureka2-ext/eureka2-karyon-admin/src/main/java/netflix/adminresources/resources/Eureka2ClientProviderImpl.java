package netflix.adminresources.resources;

import com.google.inject.Singleton;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;

@Singleton
public class Eureka2ClientProviderImpl implements Eureka2ClientProvider {
    public static final String CONFIG_DISCOVERY_PORT = "eureka.client.discovery-endpoint.port";
    public static final String CONFIG_DISCOVERY_DNS = "eureka.client.discovery-endpoint.dns";
    private int port = ConfigurationManager.getConfigInstance().getInt(CONFIG_DISCOVERY_PORT, 12001);
    private String discoveryDNS = ConfigurationManager.getConfigInstance().getString(CONFIG_DISCOVERY_DNS, "localhost");

    @Override
    public EurekaInterestClient get() {
        return EurekaClientBuilder
                .discoveryBuilder()
                .withReadServerResolver(ServerResolvers.forDnsName(discoveryDNS, port))
                .build();
    }
}

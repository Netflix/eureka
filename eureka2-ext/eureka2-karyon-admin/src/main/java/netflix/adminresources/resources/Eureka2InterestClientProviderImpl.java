package netflix.adminresources.resources;

import com.google.inject.Singleton;
import com.netflix.config.ConfigurationManager;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Eureka2InterestClientProviderImpl implements Eureka2InterestClientProvider {

    private static final Logger logger = LoggerFactory.getLogger(Eureka2InterestClientProviderImpl.class);

    public static final String CONFIG_DISCOVERY_PORT = "eureka.client.discovery-endpoint.port";
    public static final String CONFIG_DISCOVERY_DNS = "eureka.client.discovery-endpoint.dns";
    private int port = ConfigurationManager.getConfigInstance().getInt(CONFIG_DISCOVERY_PORT, 12001);
    private String discoveryDNS = ConfigurationManager.getConfigInstance().getString(CONFIG_DISCOVERY_DNS, "localhost");

    @Override
    public EurekaInterestClient get() {
        logger.info("Subscribing to Eureka2 server {}:{}", discoveryDNS, port);
        return new EurekaInterestClientBuilder()
                .fromDns(discoveryDNS, port)
                .build();
    }
}

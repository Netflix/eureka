package netflix.admin.eureka;

import com.google.inject.Provider;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;


public class EurekaClientDefaultProviderImpl implements Provider<EurekaClient> {
    @Override
    public EurekaClient get() {
        return Eureka.newClientBuilder(ServerResolvers.forDnsName("us-east-1.eureka2.discoveryprod.netflix.net", 12103)).build();
    }
}

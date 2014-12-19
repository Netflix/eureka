package netflix.admin.eureka;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;


public class EurekaClientDefaultProvider implements EurekaClientProvider {
    @Override
    public EurekaClient get() {
        return Eureka.newClientBuilder(ServerResolvers.forDnsName("localhost", 12103)).build();
    }
}

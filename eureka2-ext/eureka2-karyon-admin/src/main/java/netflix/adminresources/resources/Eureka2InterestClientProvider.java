package netflix.adminresources.resources;

import com.google.inject.ImplementedBy;
import com.netflix.eureka2.client.EurekaInterestClient;

@ImplementedBy(Eureka2InterestClientProviderImpl.class)
public interface Eureka2InterestClientProvider {
    EurekaInterestClient get();
}

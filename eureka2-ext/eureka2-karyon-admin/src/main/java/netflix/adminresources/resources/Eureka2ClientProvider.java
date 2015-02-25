package netflix.adminresources.resources;

import com.google.inject.ImplementedBy;
import com.netflix.eureka2.client.interest.EurekaInterestClient;

@ImplementedBy(Eureka2ClientProviderImpl.class)
public interface Eureka2ClientProvider {
    EurekaInterestClient get();
}

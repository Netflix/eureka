package netflix.admin.eureka;

import com.netflix.eureka2.client.EurekaClient;

public interface EurekaClientProvider {
    EurekaClient get();
}

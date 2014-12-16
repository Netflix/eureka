package netflix.admin.eureka;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class InstanceRegistryCache {
    private static final Logger logger = LoggerFactory.getLogger(EurekaResource.class);
    private final Map<String, InstanceInfo> registryCache;
    private final EurekaClient eurekaClient;

    @Inject
    public InstanceRegistryCache() {
        ServerResolver serverResolver = ServerResolvers.just("localhost", 12103);
        eurekaClient = Eureka.newClient(serverResolver);
        registryCache = new ConcurrentHashMap<>();
        start();
    }

    public Map<String, InstanceInfo> get() {
       return registryCache;
    }

    private void start() {
        eurekaClient.forInterest(Interests.forFullRegistry()).retry().subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> changeNotification) {
                final ChangeNotification.Kind notificationKind = changeNotification.getKind();
                final InstanceInfo instInfo = changeNotification.getData();
                if (notificationKind == ChangeNotification.Kind.Add ||
                        notificationKind == ChangeNotification.Kind.Modify) {
                    registryCache.put(instInfo.getId(), instInfo);
                } else if (notificationKind == ChangeNotification.Kind.Delete) {
                    registryCache.remove(instInfo.getId());
                }
            }
        });
    }
}


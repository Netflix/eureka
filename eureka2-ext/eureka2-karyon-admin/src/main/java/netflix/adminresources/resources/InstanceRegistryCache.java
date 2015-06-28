package netflix.adminresources.resources;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.AbstractEurekaServer;
import rx.functions.Action1;

@Singleton
public class InstanceRegistryCache {

    private final Map<String, InstanceInfoSummary> registryCache;
    private final AbstractEurekaServer eurekaServer;

    @Inject
    public InstanceRegistryCache(AbstractEurekaServer eurekaServer) {
        this.eurekaServer = eurekaServer;
        registryCache = new ConcurrentHashMap<>();
    }

    public Map<String, InstanceInfoSummary> get() {
        return registryCache;
    }

    @PostConstruct
    public void start() {
        ServerResolver resolver = ServerResolvers.fromHostname("localhost").withPort(eurekaServer.getInterestPort());
        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder().withServerResolver(resolver).build();
        interestClient.forInterest(Interests.forFullRegistry()).retry().subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> changeNotification) {
                final ChangeNotification.Kind notificationKind = changeNotification.getKind();
                final InstanceInfo instInfo = changeNotification.getData();
                if (notificationKind == ChangeNotification.Kind.Add ||
                        notificationKind == ChangeNotification.Kind.Modify) {
                    registryCache.put(instInfo.getId(), new InstanceInfoSummary(instInfo));
                } else if (notificationKind == ChangeNotification.Kind.Delete) {
                    registryCache.remove(instInfo.getId());
                }
            }
        });
    }
}
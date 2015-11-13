package netflix.adminresources.resources.eureka.registry;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action1;

@Singleton
public class InstanceRegistryCache {
    private static final Logger logger = LoggerFactory.getLogger(InstanceRegistryCache.class);

    private final Map<String, InstanceInfoSummary> registryCache;
    private final AbstractEurekaServer eurekaServer;

    @Inject
    public InstanceRegistryCache(AbstractEurekaServer eurekaServer) {
        this.eurekaServer = eurekaServer;
        this.registryCache = new ConcurrentHashMap<>();
    }

    public Map<String, InstanceInfoSummary> get() {
        return new HashMap<>(registryCache);
    }

    @PostConstruct
    public void start() {
        eurekaServer.getEurekaRegistryView().forInterest(Interests.forFullRegistry())
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
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
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.warn("forInterest to the registry failed", throwable);
                        registryCache.clear();  // clear the cache of any stale entries, the retry should repopulate
                    }
                })
                .retryWhen(new RetryStrategyFunc(30, TimeUnit.SECONDS))
                .subscribe(new NoOpSubscriber<ChangeNotification<InstanceInfo>>());
    }
}
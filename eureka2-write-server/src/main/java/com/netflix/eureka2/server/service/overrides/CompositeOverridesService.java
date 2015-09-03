package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

import javax.inject.Inject;
import java.util.Iterator;
import java.util.Set;

/**
 * @author David Liu
 */
public class CompositeOverridesService implements OverridesService {

    private final OverridesService head;
    private volatile OverridesService tail;

    // expects an ordered set (i.e. by binding order)
    @Inject
    public CompositeOverridesService(Set<OverridesService> overridesServices) {
        if (overridesServices.isEmpty()) {
            throw new IllegalArgumentException("No override service provided");
        }

        Iterator<OverridesService> it = overridesServices.iterator();
        head = it.next();
        tail = head;
        while (it.hasNext()) {
            OverridesService next = it.next();
            tail.addOutboundHandler(next);
            tail = next;
        }
    }

    @Override
    public void addOutboundHandler(EurekaRegistrationProcessor<InstanceInfo> outboundHandler) {
        tail.addOutboundHandler(outboundHandler);
    }

    @Override
    public Observable<Void> connect(String id, Source source, Observable<ChangeNotification<InstanceInfo>> registrationUpdates) {
        return head.connect(id, source, registrationUpdates);
    }

    @Override
    public Observable<Void> shutdown() {
        return head.shutdown();  // chained
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return head.shutdown(cause);  // chained
    }

    @Override
    public Observable<Integer> sizeObservable() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }
}

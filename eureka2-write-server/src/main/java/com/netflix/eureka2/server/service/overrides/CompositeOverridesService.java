package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
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
    public Observable<Void> register(String id, Observable<InstanceInfo> registrationUpdates, Source source) {
        return head.register(id, registrationUpdates, source);
    }

    @Override
    public Observable<Boolean> register(InstanceInfo registrant, Source source) {
        return head.register(registrant, source);
    }

    @Override
    public Observable<Boolean> unregister(InstanceInfo registrant, Source source) {
        return head.unregister(registrant, source);
    }

    @Override
    public Observable<Void> shutdown() {
        return head.shutdown();  // chained
    }

    @Override
    public Observable<Void> shutdown(Throwable cause) {
        return head.shutdown(cause);  // chained
    }
}

package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface RegistryBootstrapService {

    Observable<Void> loadIntoRegistry(EurekaRegistry<InstanceInfo> registry, Source source);
}

package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public interface RegistryBootstrapService {

     Observable<Void> loadIntoRegistry(SourcedEurekaRegistry<InstanceInfo> registry, Source source);
}

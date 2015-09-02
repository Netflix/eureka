package com.netflix.eureka2.server;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.service.EurekaShutdownService;
import com.netflix.eureka2.server.transport.tcp.interest.TcpInterestServer;
import com.netflix.governator.LifecycleShutdownSignal;
import netflix.adminresources.AdminResourcesContainer;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractEurekaServer {

    protected final Injector injector;

    public AbstractEurekaServer(Injector injector) {
        this.injector = injector;
    }

    public Injector getInjector() {
        return injector;
    }

    public int getInterestPort() {
        return injector.getInstance(TcpInterestServer.class).serverPort();
    }

    public int getHttpServerPort() {
        Binding<EurekaHttpServer> binding = injector.getExistingBinding(Key.get(EurekaHttpServer.class));
        if (binding != null) {
            return binding.getProvider().get().serverPort();
        }
        return -1;
    }

    public int getShutdownPort() {
        Binding<EurekaShutdownService> binding = injector.getExistingBinding(Key.get(EurekaShutdownService.class));
        if (binding != null) {
            return binding.getProvider().get().getShutdownPort();
        }
        return -1;
    }

    public int getWebAdminPort() {
        Binding<AdminResourcesContainer> binding = injector.getExistingBinding(Key.get(AdminResourcesContainer.class));
        if (binding != null) {
            return binding.getProvider().get().getServerPort();
        }
        return -1;
    }

    public EurekaRegistryView<InstanceInfo> getEurekaRegistryView() {
        return injector.getInstance(EurekaRegistryView.class);
    }

    public void shutdown() {
        LifecycleShutdownSignal signal = injector.getInstance(LifecycleShutdownSignal.class);
        signal.signal();
    }
}

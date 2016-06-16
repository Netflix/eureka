package com.netflix.discovery;

import javax.inject.Provider;

import org.junit.Test;

import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.discovery.DiscoveryClient.DiscoveryClientOptionalArgs;

/**
 * @author Matt Nelson
 */
public class DiscoveryClientOptionalArgsTest {
    
    @Test
    public void testHealthCheckCallbackGuiceProvider() {
        DiscoveryClientOptionalArgs args = new DiscoveryClientOptionalArgs();
        args.setHealthCheckCallbackProvider(new GuiceProvider<HealthCheckCallback>());
    }
    
    @Test
    public void testHealthCheckCallbackJavaxProvider() {
        DiscoveryClientOptionalArgs args = new DiscoveryClientOptionalArgs();
        args.setHealthCheckCallbackProvider(new JavaxProvider<HealthCheckCallback>());
    }
    
    @Test
    public void testHealthCheckHandlerGuiceProvider() {
        DiscoveryClientOptionalArgs args = new DiscoveryClientOptionalArgs();
        args.setHealthCheckHandlerProvider(new GuiceProvider<HealthCheckHandler>());
    }
    
    @Test
    public void testHealthCheckHandlerJavaxProvider() {
        DiscoveryClientOptionalArgs args = new DiscoveryClientOptionalArgs();
        args.setHealthCheckHandlerProvider(new JavaxProvider<HealthCheckHandler>());
    }
    
    private class JavaxProvider<T> implements Provider<T> {
        @Override
        public T get() {
            return null;
        }
    }
    
    private class GuiceProvider<T> implements com.google.inject.Provider<T> {
        
        @Override
        public T get() {
            return null;
        }
    }
}

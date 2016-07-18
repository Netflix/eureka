package com.netflix.discovery;

import javax.inject.Provider;

import org.junit.Before;
import org.junit.Test;

import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.discovery.shared.transport.jersey.DiscoveryClientOptionalArgs;

/**
 * @author Matt Nelson
 */
public class DiscoveryClientOptionalArgsTest {
    
    private DiscoveryClientOptionalArgs args;
    
    @Before
    public void before() {
        args = new DiscoveryClientOptionalArgs();
    }
    
    @Test
    public void testHealthCheckCallbackGuiceProvider() {
        args.setHealthCheckCallbackProvider(new GuiceProvider<HealthCheckCallback>());
    }
    
    @Test
    public void testHealthCheckCallbackJavaxProvider() {
        args.setHealthCheckCallbackProvider(new JavaxProvider<HealthCheckCallback>());
    }
    
    @Test
    public void testHealthCheckHandlerGuiceProvider() {
        args.setHealthCheckHandlerProvider(new GuiceProvider<HealthCheckHandler>());
    }
    
    @Test
    public void testHealthCheckHandlerJavaxProvider() {
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

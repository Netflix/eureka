package com.netflix.discovery;

import javax.inject.Provider;

import com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs;
import org.junit.Before;
import org.junit.Test;

import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;

/**
 * @author Matt Nelson
 */
public class Jersey1DiscoveryClientOptionalArgsTest {
    
    private Jersey1DiscoveryClientOptionalArgs args;
    
    @Before
    public void before() {
        args = new Jersey1DiscoveryClientOptionalArgs();
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

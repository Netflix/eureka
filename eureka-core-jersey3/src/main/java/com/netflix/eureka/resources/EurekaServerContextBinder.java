package com.netflix.eureka.resources;

//import org.glassfish.hk2.api.Factory;
//import org.glassfish.hk2.utilities.binding.AbstractBinder;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerContextHolder;
import org.glassfish.jersey.internal.inject.AbstractBinder;

/**
 * Jersey3 binder for the EurekaServerContext. Replaces the GuiceFilter in the server WAR web.xml
 * @author Matt Nelson
 */
public class EurekaServerContextBinder extends AbstractBinder {
    
    /*public class EurekaServerContextFactory implements Factory<EurekaServerContext> {
        @Override
        public EurekaServerContext provide() {
           return EurekaServerContextHolder.getInstance().getServerContext();
        }
     
        @Override
        public void dispose(EurekaServerContext t) {
        }
    }*/

    /**
     * {@inheritDoc}
     */
    @Override
    protected void configure() {
        bindFactory(() -> EurekaServerContextHolder.getInstance().getServerContext()).to(EurekaServerContext.class);
    }
}

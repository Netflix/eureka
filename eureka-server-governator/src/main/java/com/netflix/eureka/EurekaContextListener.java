package com.netflix.eureka;

import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.eureka.util.EurekaMonitors;
import com.netflix.governator.LifecycleInjector;
import com.netflix.governator.guice.servlet.GovernatorServletContextListener;
import com.thoughtworks.xstream.XStream;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * @author David Liu
 */
public class EurekaContextListener extends GovernatorServletContextListener {

    private EurekaServerContext serverContext;

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        super.contextInitialized(servletContextEvent);
        ServletContext sc = servletContextEvent.getServletContext();
        sc.setAttribute(EurekaServerContext.class.getName(), serverContext);

        // Copy registry from neighboring eureka node
        int registryCount = serverContext.getRegistry().syncUp();
        serverContext.getRegistry().openForTraffic(serverContext.getApplicationInfoManager(), registryCount);

        // Register all monitoring statistics.
        EurekaMonitors.registerAllStats();
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        EurekaMonitors.shutdown();

        ServletContext sc = servletContextEvent.getServletContext();
        sc.removeAttribute(EurekaServerContext.class.getName());
        super.contextDestroyed(servletContextEvent);
    }

    @Override
    protected LifecycleInjector createInjector() {
        // For backward compatibility
        JsonXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(), XStream.PRIORITY_VERY_HIGH);
        XmlXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(), XStream.PRIORITY_VERY_HIGH);

        LifecycleInjector injector = EurekaInjectorCreator.createInjector();
        serverContext = injector.getInstance(EurekaServerContext.class);
        return injector;
    }
}

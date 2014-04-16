package com.netflix.discovery;


import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Nitesh Kant
 */
public class MockRemoteEurekaServer extends ExternalResource {

    public static final String EUREKA_API_BASE_PATH = "/eureka/v2/";

    private int port = 0;
    private final Map<String, Application> applicationMap        = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionApps      = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionAppsDelta = new HashMap<String, Application>();
    private final Map<String, Application> applicationDeltaMap   = new HashMap<String, Application>();
    private Server server;
    private AtomicBoolean sentDelta = new AtomicBoolean();
    private AtomicBoolean sentRegistry = new AtomicBoolean();

    public MockRemoteEurekaServer() {
    }

    protected void before() throws Throwable {
        start();
    }

    protected void after() {
        try {
            stop();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public void start() throws Exception {
        server = new Server(this.port);
        server.setHandler(new AppsResourceHandler());
        server.start();
        port = server.getConnectors()[0].getLocalPort();
    }

    public int getPort() {
        return port;
    }
    
    public void stop() throws Exception {
        server.stop();
        server = null;
        port = 0;
        
        applicationMap.clear();
        remoteRegionApps.clear();
        remoteRegionAppsDelta.clear();
        applicationDeltaMap.clear();
    }

    public boolean isSentDelta() {
        return sentDelta.get();
    }

    public boolean isSentRegistry() {
        return sentRegistry.get();
    }

    private class AppsResourceHandler extends AbstractHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            System.out.println("Eureka port: " + port + ". " + System.currentTimeMillis() +
                               ". Eureka resource mock, received request on path: " + pathInfo + ". HTTP method: |"
                               + request.getMethod() + "|" + ", query string: " + request.getQueryString());
            boolean handled = false;
            if (null != pathInfo && pathInfo.startsWith("")) {
                pathInfo = pathInfo.substring(EUREKA_API_BASE_PATH.length());
                boolean includeRemote = isRemoteRequest(request);

                if (pathInfo.startsWith("apps/delta")) {
                    Applications apps = new Applications();
                    apps.setVersion(100l);
                    if (sentDelta.compareAndSet(false, true)) {
                        addDeltaApps(includeRemote, apps);
                    } else {
                        System.out.println("Eureka port: " +  port + ". " + System.currentTimeMillis() +". Not including delta as it has already been sent.");
                    }
                    apps.setAppsHashCode(getDeltaAppsHashCode(includeRemote));
                    sendOkResponseWithContent((Request) request, response, apps);
                    handled = true;
                } else if(pathInfo.startsWith("apps")) {
                    Applications apps = new Applications();
                    apps.setVersion(100l);
                    for (Application application : applicationMap.values()) {
                        apps.addApplication(application);
                    }
                    if (includeRemote) {
                        for (Application application : remoteRegionApps.values()) {
                            apps.addApplication(application);
                        }
                    }

                    if (sentDelta.get()) {
                        addDeltaApps(includeRemote, apps);
                    } else {
                        System.out.println("Eureka port: " + port + ". " + System.currentTimeMillis() +". Not including delta apps in /apps response, as delta has not been sent.");
                    }
                    apps.setAppsHashCode(apps.getReconcileHashCode());
                    sendOkResponseWithContent((Request) request, response, apps);
                    sentRegistry.set(true);
                    handled = true;
                }
            }

            if(!handled) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   "Request path: " + pathInfo + " not supported by eureka resource mock.");
            }
        }

        private void addDeltaApps(boolean includeRemote, Applications apps) {
            for (Application application : applicationDeltaMap.values()) {
                apps.addApplication(application);
            }
            if (includeRemote) {
                for (Application application : remoteRegionAppsDelta.values()) {
                    apps.addApplication(application);
                }
            }
        }

        private String getDeltaAppsHashCode(boolean includeRemote) {
            Applications allApps = new Applications();
            for (Application application : applicationMap.values()) {
                allApps.addApplication(application);
            }

            if (includeRemote) {
                for (Application application : remoteRegionApps.values()) {
                    allApps.addApplication(application);
                }
            }
            addDeltaApps(includeRemote, allApps);
            return allApps.getReconcileHashCode();
        }

        private boolean isRemoteRequest(HttpServletRequest request) {
            String queryString = request.getQueryString();
            if (queryString == null)
                return false;
            return queryString.contains("regions=");
        }

        private void sendOkResponseWithContent(Request request, HttpServletResponse response, Applications apps)
                throws IOException {
            String content = XmlXStream.getInstance().toXML(apps);
            response.setContentType("application/xml");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(content);
            response.getWriter().flush();
            request.setHandled(true);
            System.out.println("Eureka port: " + port + ". " + System.currentTimeMillis() +
                               ". Eureka resource mock, sent response for request path: " + request.getPathInfo() +
                               ", apps count: " + apps.getRegisteredApplications().size());
        }
    }

    public void addRemoteRegionApps(String appName, Application app) {
        this.remoteRegionApps.put(appName, app);
    }

    public void addRemoteRegionAppsDelta(String appName, Application app) {
        this.remoteRegionAppsDelta.put(appName, app);
    }

    public void addLocalRegionApps(String appName, Application app) {
        this.applicationMap.put(appName, app);
    }

    public void addLocalRegionAppsDelta(String appName, Application app) {
        this.applicationDeltaMap.put(appName, app);
    }

    public void waitForDeltaToBeRetrieved(int refreshRate) throws InterruptedException {
        int count = 0;
        while (count < 3 && !isSentDelta()) {
            System.out.println("Sleeping for " + refreshRate + " seconds to let the remote registry fetch delta. Attempt: " + count);
            Thread.sleep( 3 * refreshRate * 1000);
            System.out.println("Done sleeping for 10 seconds to let the remote registry fetch delta. Delta fetched: " + isSentDelta());
        }

        System.out.println("Sleeping for extra " + refreshRate + " seconds for the client to update delta in memory.");
    }

}
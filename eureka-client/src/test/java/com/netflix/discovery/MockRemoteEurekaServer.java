package com.netflix.discovery;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.appinfo.AbstractEurekaIdentity;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaClientIdentity;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

/**
 * @author Nitesh Kant
 */
public class MockRemoteEurekaServer extends ExternalResource {

    public static final String EUREKA_API_BASE_PATH = "/eureka/v2/";

    private static Pattern HOSTNAME_PATTERN = Pattern.compile("\"hostName\"\\s?:\\s?\\\"([A-Za-z0-9\\.-]*)\\\"");
    private static Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s?:\\s?\\\"([A-Z_]*)\\\"");

    private int port;
    private final Map<String, Application> applicationMap = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionApps = new HashMap<String, Application>();
    private final Map<String, Application> remoteRegionAppsDelta = new HashMap<String, Application>();
    private final Map<String, Application> applicationDeltaMap = new HashMap<String, Application>();
    private Server server;
    private final AtomicBoolean sentDelta = new AtomicBoolean();
    private final AtomicBoolean sentRegistry = new AtomicBoolean();

    public final BlockingQueue<String> registrationStatusesQueue = new LinkedBlockingQueue<>();
    public final List<String> registrationStatuses = new ArrayList<String>();

    public final AtomicLong registerCount = new AtomicLong(0);
    public final AtomicLong heartbeatCount = new AtomicLong(0);
    public final AtomicLong getFullRegistryCount = new AtomicLong(0);
    public final AtomicLong getSingleVipCount = new AtomicLong(0);
    public final AtomicLong getDeltaCount = new AtomicLong(0);

    @Override
    protected void before() throws Throwable {
        start();
    }

    @Override
    protected void after() {
        try {
            stop();
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public void start() throws Exception {
        server = new Server(port);
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

        registrationStatusesQueue.clear();
        registrationStatuses.clear();

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

    public void addRemoteRegionApps(String appName, Application app) {
        remoteRegionApps.put(appName, app);
    }

    public void addRemoteRegionAppsDelta(String appName, Application app) {
        remoteRegionAppsDelta.put(appName, app);
    }

    public void addLocalRegionApps(String appName, Application app) {
        applicationMap.put(appName, app);
    }

    public void addLocalRegionAppsDelta(String appName, Application app) {
        applicationDeltaMap.put(appName, app);
    }

    public void waitForDeltaToBeRetrieved(int refreshRate) throws InterruptedException {
        int count = 0;
        while (count++ < 3 && !isSentDelta()) {
            System.out.println("Sleeping for " + refreshRate + " seconds to let the remote registry fetch delta. Attempt: " + count);
            Thread.sleep(3 * refreshRate * 1000);
            System.out.println("Done sleeping for 10 seconds to let the remote registry fetch delta. Delta fetched: " + isSentDelta());
        }

        System.out.println("Sleeping for extra " + refreshRate + " seconds for the client to update delta in memory.");
    }

    //
    // A base default resource handler for the mock server
    //
    private class AppsResourceHandler extends AbstractHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException, ServletException {
            String authName = request.getHeader(AbstractEurekaIdentity.AUTH_NAME_HEADER_KEY);
            String authVersion = request.getHeader(AbstractEurekaIdentity.AUTH_VERSION_HEADER_KEY);
            String authId = request.getHeader(AbstractEurekaIdentity.AUTH_ID_HEADER_KEY);

            Assert.assertEquals(EurekaClientIdentity.DEFAULT_CLIENT_NAME, authName);
            Assert.assertNotNull(authVersion);
            Assert.assertNotNull(authId);

            String pathInfo = request.getPathInfo();
            System.out.println("Eureka port: " + port + ". " + System.currentTimeMillis() +
                    ". Eureka resource mock, received request on path: " + pathInfo + ". HTTP method: |"
                    + request.getMethod() + '|' + ", query string: " + request.getQueryString());
            boolean handled = false;
            if (null != pathInfo && pathInfo.startsWith("")) {
                pathInfo = pathInfo.substring(EUREKA_API_BASE_PATH.length());
                boolean includeRemote = isRemoteRequest(request);

                if (pathInfo.startsWith("apps/delta")) {
                    getDeltaCount.getAndIncrement();

                    Applications apps = new Applications();
                    apps.setVersion(100L);
                    if (sentDelta.compareAndSet(false, true)) {
                        addDeltaApps(includeRemote, apps);
                    } else {
                        System.out.println("Eureka port: " + port + ". " + System.currentTimeMillis() + ". Not including delta as it has already been sent.");
                    }
                    apps.setAppsHashCode(getDeltaAppsHashCode(includeRemote));
                    sendOkResponseWithContent((Request) request, response, apps);
                    handled = true;
                } else if (pathInfo.equals("apps/")) {
                    getFullRegistryCount.getAndIncrement();

                    Applications apps = new Applications();
                    apps.setVersion(100L);
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
                        System.out.println("Eureka port: " + port + ". " + System.currentTimeMillis() + ". Not including delta apps in /apps response, as delta has not been sent.");
                    }
                    apps.setAppsHashCode(apps.getReconcileHashCode());
                    sendOkResponseWithContent((Request) request, response, apps);
                    sentRegistry.set(true);
                    handled = true;
                } else if (pathInfo.startsWith("vips/")) {
                    getSingleVipCount.getAndIncrement();

                    String vipAddress = pathInfo.substring("vips/".length());
                    Applications apps = new Applications();
                    apps.setVersion(-1l);
                    for (Application application : applicationMap.values()) {
                        Application retApp = new Application(application.getName());
                        for (InstanceInfo instance : application.getInstances()) {
                            if (vipAddress.equals(instance.getVIPAddress())) {
                                retApp.addInstance(instance);
                            }
                        }

                        if (retApp.getInstances().size() > 0) {
                            apps.addApplication(retApp);
                        }
                    }

                    apps.setAppsHashCode(apps.getReconcileHashCode());
                    sendOkResponseWithContent((Request) request, response, apps);
                    handled = true;
                } else if (pathInfo.startsWith("apps")) {  // assume this is the renewal heartbeat
                    if (request.getMethod().equals("PUT")) {  // this is the renewal heartbeat
                        heartbeatCount.getAndIncrement();
                    } else if (request.getMethod().equals("POST")) {  // this is a register request
                        registerCount.getAndIncrement();
                        String statusStr = null;
                        String hostname = null;
                        String line;
                        BufferedReader reader = request.getReader();
                        while ((line = reader.readLine()) != null) {
                            Matcher hostNameMatcher = HOSTNAME_PATTERN.matcher(line);
                            if (hostname == null && hostNameMatcher.find()) {
                                hostname = hostNameMatcher.group(1);
                                // don't break here as we want to read the full buffer for a clean connection close
                            }

                            Matcher statusMatcher = STATUS_PATTERN.matcher(line);
                            if (statusStr == null && statusMatcher.find()) {
                                statusStr = statusMatcher.group(1);
                                // don't break here as we want to read the full buffer for a clean connection close
                            }
                        }
                        System.out.println("Matched status to: " + statusStr);
                        registrationStatusesQueue.add(statusStr);
                        registrationStatuses.add(statusStr);

                        String appName = pathInfo.substring(5);
                        if (!applicationMap.containsKey(appName)) {
                            Application app = new Application(appName);
                            InstanceInfo instanceInfo = InstanceInfo.Builder.newBuilder()
                                    .setAppName(appName)
                                    .setIPAddr("1.1.1.1")
                                    .setHostName(hostname)
                                    .setStatus(InstanceInfo.InstanceStatus.toEnum(statusStr))
                                    .setDataCenterInfo(new DataCenterInfo() {
                                        @Override
                                        public Name getName() {
                                            return Name.MyOwn;
                                        }
                                    })
                                    .build();
                            app.addInstance(instanceInfo);
                            applicationMap.put(appName, app);
                        }
                    }
                    Applications apps = new Applications();
                    apps.setAppsHashCode("");
                    sendOkResponseWithContent((Request) request, response, apps);
                    handled = true;
                } else {
                    System.out.println("Not handling request: " + pathInfo);
                }
            }

            if (!handled) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Request path: " + pathInfo + " not supported by eureka resource mock.");
            }
        }

        protected void addDeltaApps(boolean includeRemote, Applications apps) {
            for (Application application : applicationDeltaMap.values()) {
                apps.addApplication(application);
            }
            if (includeRemote) {
                for (Application application : remoteRegionAppsDelta.values()) {
                    apps.addApplication(application);
                }
            }
        }

        protected String getDeltaAppsHashCode(boolean includeRemote) {
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

        protected boolean isRemoteRequest(HttpServletRequest request) {
            String queryString = request.getQueryString();
            if (queryString == null) {
                return false;
            }
            return queryString.contains("regions=");
        }

        protected void sendOkResponseWithContent(Request request, HttpServletResponse response, Applications apps)
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

        protected void sleep(int seconds) {
            try {
                Thread.sleep(seconds);
            } catch (InterruptedException e) {
                System.out.println("Interrupted: " + e);
            }
        }
    }
}
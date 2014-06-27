package com.netflix.eureka.mock;

import com.netflix.appinfo.AbstractEurekaAuthInfo;
import com.netflix.appinfo.EurekaClientAuthInfo;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka.EurekaServerAuthInfo;
import com.netflix.eureka.RequestAuthFilter;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.servlet.FilterHolder;
import org.mortbay.jetty.servlet.ServletHandler;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author Nitesh Kant
 */
public class MockRemoteEurekaServer extends ExternalResource {

    public static final String EUREKA_API_BASE_PATH = "/eureka/v2/";

    private final Map<String, Application> applicationMap;
    private final Map<String, Application> applicationDeltaMap;
    private final Server server;
    private boolean sentDelta;
    private int port;

    public MockRemoteEurekaServer(int port, Map<String, Application> applicationMap,
                                  Map<String, Application> applicationDeltaMap) {
        this.applicationMap = applicationMap;
        this.applicationDeltaMap = applicationDeltaMap;
        ServletHandler handler = new AppsResourceHandler();
        handler.addFilterWithMapping(RequestAuthFilter.class, "/*", 1);
        server = new Server(port);
        server.addHandler(handler);
        System.out.println(String.format(
                "Created eureka server mock with applications map %s and applications delta map %s",
                stringifyAppMap(applicationMap), stringifyAppMap(applicationDeltaMap)));
    }

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
        server.start();
        port = server.getConnectors()[0].getLocalPort();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public boolean isSentDelta() {
        return sentDelta;
    }

    public int getPort() {
        return port;
    }

    private static String stringifyAppMap(Map<String, Application> applicationMap) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Application> entry : applicationMap.entrySet()) {
            String entryAsString = String.format("{ name : %s , instance count: %d }", entry.getKey(),
                                                 entry.getValue().getInstances().size());
            builder.append(entryAsString);
        }
        return builder.toString();
    }

    private class AppsResourceHandler extends ServletHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException, ServletException {

            String authName = request.getHeader(AbstractEurekaAuthInfo.AUTH_NAME_HEADER_KEY);
            String authVersion = request.getHeader(AbstractEurekaAuthInfo.AUTH_VERSION_HEADER_KEY);
            String authId = request.getHeader(AbstractEurekaAuthInfo.AUTH_ID_HEADER_KEY);

            Assert.assertNotNull(authName);
            Assert.assertNotNull(authVersion);
            Assert.assertNotNull(authId);

            Assert.assertTrue( !authName.equals(RequestAuthFilter.UNKNOWN) );
            Assert.assertTrue( !authVersion.equals(RequestAuthFilter.UNKNOWN) );
            Assert.assertTrue( !authId.equals(RequestAuthFilter.UNKNOWN) );

            for (FilterHolder filterHolder : this.getFilters()) {
                filterHolder.getFilter().doFilter(request, response, new FilterChain() {
                    @Override
                    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
                        // do nothing;
                    }
                });
            }

            String pathInfo = request.getPathInfo();
            System.out.println(
                    "Eureka resource mock, received request on path: " + pathInfo + ". HTTP method: |" + request
                            .getMethod() + '|');
            boolean handled = false;
            if (null != pathInfo && pathInfo.startsWith("")) {
                pathInfo = pathInfo.substring(EUREKA_API_BASE_PATH.length());
                if (pathInfo.startsWith("apps/delta")) {
                    Applications apps = new Applications();
                    for (Application application : applicationDeltaMap.values()) {
                        apps.addApplication(application);
                    }
                    apps.setAppsHashCode(apps.getReconcileHashCode());
                    sendOkResponseWithContent((Request) request, response, XmlXStream.getInstance().toXML(apps));
                    handled = true;
                    sentDelta = true;
                } else if(pathInfo.startsWith("apps")) {
                    Applications apps = new Applications();
                    for (Application application : applicationMap.values()) {
                        apps.addApplication(application);
                    }
                    apps.setAppsHashCode(apps.getReconcileHashCode());
                    sendOkResponseWithContent((Request) request, response, XmlXStream.getInstance().toXML(apps));
                    handled = true;
                }
            }

            if(!handled) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   "Request path: " + pathInfo + " not supported by eureka resource mock.");
            }
        }

        private void sendOkResponseWithContent(Request request, HttpServletResponse response, String content)
                throws IOException {
            response.setContentType("application/xml");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(content);
            response.getWriter().flush();
            request.setHandled(true);
            System.out.println("Eureka resource mock, sent response for request path: " + request.getPathInfo() +
                               " with content" + content);
        }
    }

}

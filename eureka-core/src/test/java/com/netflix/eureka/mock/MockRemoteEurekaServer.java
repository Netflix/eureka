package com.netflix.eureka.mock;

import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * @author Nitesh Kant
 */
public class MockRemoteEurekaServer {

    public static final String EUREKA_API_BASE_PATH = "/eureka/v2/";

    private final Map<String, Application> applicationMap;
    private final Map<String, Application> applicationDeltaMap;
    private final Server server;
    private boolean sentDelta;

    public MockRemoteEurekaServer(int port, Map<String, Application> applicationMap,
                                  Map<String, Application> applicationDeltaMap) {
        this.applicationMap = applicationMap;
        this.applicationDeltaMap = applicationDeltaMap;
        server = new Server(port);
        server.setHandler(new AppsResourceHandler());
        System.out.println(String.format(
                "Created eureka server mock with applications map %s and applications delta map %s",
                stringifyAppMap(applicationMap), stringifyAppMap(applicationDeltaMap)));
    }

    public void start() throws Exception {
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public boolean isSentDelta() {
        return sentDelta;
    }

    private String stringifyAppMap(Map<String, Application> applicationMap) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Application> entry : applicationMap.entrySet()) {
            String entryAsString = String.format("{ name : %s , instance count: %d }", entry.getKey(),
                                                 entry.getValue().getInstances().size());
            builder.append(entryAsString);
        }
        return builder.toString();
    }

    private class AppsResourceHandler extends AbstractHandler {

        @Override
        public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch)
                throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            System.out.println(
                    "Eureka resource mock, received request on path: " + pathInfo + ". HTTP method: |" + request
                            .getMethod() + "|");
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
                               " with content" + String.valueOf(content));
        }
    }

}

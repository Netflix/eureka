/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.transport;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.wrappers.CodecWrappers;
import com.netflix.discovery.converters.wrappers.CodecWrappers.JacksonJson;
import com.netflix.discovery.converters.wrappers.DecoderWrapper;
import com.netflix.discovery.converters.wrappers.EncoderWrapper;
import com.netflix.discovery.shared.Applications;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP server with Eureka compatible REST API that delegates client request to the provided {@link EurekaHttpClient}
 * implementation. It is very lightweight implementation that can be used in unit test without incurring to much
 * overhead.
 *
 * @author Tomasz Bak
 */
public class SimpleEurekaHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEurekaHttpServer.class);

    private final EurekaHttpClient requestHandler;
    private final EurekaTransportEventListener eventListener;
    private final HttpServer httpServer;

    private final EncoderWrapper encoder = CodecWrappers.getEncoder(JacksonJson.class);
    private final DecoderWrapper decoder = CodecWrappers.getDecoder(JacksonJson.class);

    public SimpleEurekaHttpServer(EurekaHttpClient requestHandler) throws IOException {
        this(requestHandler, null);
    }

    public SimpleEurekaHttpServer(EurekaHttpClient requestHandler, EurekaTransportEventListener eventListener) throws IOException {
        this.requestHandler = requestHandler;
        this.eventListener = eventListener;

        this.httpServer = HttpServer.create(new InetSocketAddress(0), 1);
        httpServer.createContext("/v2", createEurekaV2Handle());
        httpServer.setExecutor(null);
        httpServer.start();
    }

    public void shutdown() {
        httpServer.stop(0);
    }

    public URI getServiceURI() {
        try {
            return new URI("http://localhost:" + getServerPort() + "/v2/");
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot parse service URI", e);
        }
    }

    public int getServerPort() {
        return httpServer.getAddress().getPort();
    }

    private HttpHandler createEurekaV2Handle() {
        return new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                if(eventListener != null) {
                    eventListener.onHttpRequest(mapToEurekaHttpRequest(httpExchange));
                }
                try {
                    String method = httpExchange.getRequestMethod();
                    String path = httpExchange.getRequestURI().getPath();
                    if (path.startsWith("/v2/apps")) {
                        if ("GET".equals(method)) {
                            handleAppsGET(httpExchange);
                        } else if ("POST".equals(method)) {
                            handleAppsPost(httpExchange);
                        } else if ("PUT".equals(method)) {
                            handleAppsPut(httpExchange);
                        } else if ("DELETE".equals(method)) {
                            handleAppsDelete(httpExchange);
                        } else {
                            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
                        }
                    } else if (path.startsWith("/v2/vips")) {
                        handleVipsGET(httpExchange);
                    } else if (path.startsWith("/v2/svips")) {
                        handleSecureVipsGET(httpExchange);
                    } else if (path.startsWith("/v2/instances")) {
                        handleInstanceGET(httpExchange);
                    }
                } catch (Exception e) {
                    logger.error("HttpServer error", e);
                    httpExchange.sendResponseHeaders(500, 0);
                }
                httpExchange.close();
            }
        };
    }

    private void handleAppsGET(HttpExchange httpExchange) throws IOException {
        EurekaHttpResponse<?> httpResponse;
        String path = httpExchange.getRequestURI().getPath();

        Matcher matcher;
        if (path.matches("/v2/apps[/]?")) {
            String regions = getQueryParam(httpExchange, "regions");
            httpResponse = regions == null ? requestHandler.getApplications() : requestHandler.getApplications(regions);
        } else if (path.matches("/v2/apps/delta[/]?")) {
            String regions = getQueryParam(httpExchange, "regions");
            httpResponse = regions == null ? requestHandler.getDelta() : requestHandler.getDelta(regions);
        } else if ((matcher = Pattern.compile("/v2/apps/([^/]+)/([^/]+)").matcher(path)).matches()) {
            httpResponse = requestHandler.getInstance(matcher.group(1), matcher.group(2));
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
            return;
        }
        if (httpResponse == null) {
            httpResponse = EurekaHttpResponse.anEurekaHttpResponse(HttpServletResponse.SC_NOT_FOUND).build();
        }
        mapResponse(httpExchange, httpResponse);
    }

    private void handleAppsPost(HttpExchange httpExchange) throws IOException {
        EurekaHttpResponse<?> httpResponse;
        String path = httpExchange.getRequestURI().getPath();

        if (path.matches("/v2/apps/([^/]+)(/)?")) {
            InstanceInfo instance = decoder.decode(httpExchange.getRequestBody(), InstanceInfo.class);
            httpResponse = requestHandler.register(instance);
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
            return;
        }
        mapResponse(httpExchange, httpResponse);
    }

    private void handleAppsPut(HttpExchange httpExchange) throws IOException {
        EurekaHttpResponse<?> httpResponse;
        String path = httpExchange.getRequestURI().getPath();

        Matcher matcher;
        if ((matcher = Pattern.compile("/v2/apps/([^/]+)/([^/]+)").matcher(path)).matches()) {
            String overriddenstatus = getQueryParam(httpExchange, "overriddenstatus");
            httpResponse = requestHandler.sendHeartBeat(
                    matcher.group(1), matcher.group(2), null,
                    overriddenstatus == null ? null : InstanceStatus.valueOf(overriddenstatus)
            );
        } else if ((matcher = Pattern.compile("/v2/apps/([^/]+)/([^/]+)/status").matcher(path)).matches()) {
            String newStatus = getQueryParam(httpExchange, "value");
            httpResponse = requestHandler.statusUpdate(
                    matcher.group(1), matcher.group(2),
                    newStatus == null ? null : InstanceStatus.valueOf(newStatus),
                    null
            );
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
            return;
        }
        mapResponse(httpExchange, httpResponse);
    }

    private void handleAppsDelete(HttpExchange httpExchange) throws IOException {
        EurekaHttpResponse<?> httpResponse;
        String path = httpExchange.getRequestURI().getPath();

        Matcher matcher;
        if ((matcher = Pattern.compile("/v2/apps/([^/]+)/([^/]+)").matcher(path)).matches()) {
            httpResponse = requestHandler.cancel(matcher.group(1), matcher.group(2));
        } else if ((matcher = Pattern.compile("/v2/apps/([^/]+)/([^/]+)/status").matcher(path)).matches()) {
            httpResponse = requestHandler.deleteStatusOverride(matcher.group(1), matcher.group(2), null);
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
            return;
        }
        mapResponse(httpExchange, httpResponse);
    }

    private void handleVipsGET(HttpExchange httpExchange) throws IOException {
        Matcher matcher = Pattern.compile("/v2/vips/([^/]+)").matcher(httpExchange.getRequestURI().getPath());
        if (matcher.matches()) {
            String regions = getQueryParam(httpExchange, "regions");
            EurekaHttpResponse<Applications> httpResponse = regions == null
                    ? requestHandler.getVip(matcher.group(1))
                    : requestHandler.getVip(matcher.group(1), regions);
            mapResponse(httpExchange, httpResponse);
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
        }
    }

    private void handleSecureVipsGET(HttpExchange httpExchange) throws IOException {
        Matcher matcher = Pattern.compile("/v2/svips/([^/]+)").matcher(httpExchange.getRequestURI().getPath());
        if (matcher.matches()) {
            String regions = getQueryParam(httpExchange, "regions");
            EurekaHttpResponse<Applications> httpResponse = regions == null
                    ? requestHandler.getSecureVip(matcher.group(1))
                    : requestHandler.getSecureVip(matcher.group(1), regions);
            mapResponse(httpExchange, httpResponse);
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
        }
    }

    private void handleInstanceGET(HttpExchange httpExchange) throws IOException {
        Matcher matcher = Pattern.compile("/v2/instances/([^/]+)").matcher(httpExchange.getRequestURI().getPath());
        if (matcher.matches()) {
            mapResponse(httpExchange, requestHandler.getInstance(matcher.group(1)));
        } else {
            httpExchange.sendResponseHeaders(HttpServletResponse.SC_NOT_FOUND, 0);
        }
    }

    private EurekaHttpRequest mapToEurekaHttpRequest(HttpExchange httpExchange) {
        Headers exchangeHeaders = httpExchange.getRequestHeaders();
        Map<String, String> headers = new HashMap<>();

        for(String key: exchangeHeaders.keySet()) {
            headers.put(key, exchangeHeaders.getFirst(key));
        }
        return new EurekaHttpRequest(httpExchange.getRequestMethod(), httpExchange.getRequestURI(), headers);
    }

    private <T> void mapResponse(HttpExchange httpExchange, EurekaHttpResponse<T> response) throws IOException {
        // Add headers
        for (Map.Entry<String, String> headerEntry : response.getHeaders().entrySet()) {
            httpExchange.getResponseHeaders().add(headerEntry.getKey(), headerEntry.getValue());
        }

        if (response.getStatusCode() / 100 != 2) {
            httpExchange.sendResponseHeaders(response.getStatusCode(), 0);
            return;
        }

        // Prepare body, if any
        T entity = response.getEntity();
        byte[] body = null;
        if (entity != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            encoder.encode(entity, bos);
            body = bos.toByteArray();
        }

        // Set status and body length
        httpExchange.sendResponseHeaders(response.getStatusCode(), body == null ? 0 : body.length);

        // Send body
        if (body != null) {
            OutputStream responseStream = httpExchange.getResponseBody();
            try {
                responseStream.write(body);
                responseStream.flush();
            } finally {
                responseStream.close();
            }
        }
    }

    private static String getQueryParam(HttpExchange httpExchange, String queryParam) {
        String query = httpExchange.getRequestURI().getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                String[] keyValue = part.split("=");
                if (keyValue.length > 1 && keyValue[0].equals(queryParam)) {
                    return keyValue[1];
                }
            }
        }
        return null;
    }
}

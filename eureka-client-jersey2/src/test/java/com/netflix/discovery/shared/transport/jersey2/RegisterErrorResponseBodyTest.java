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

package com.netflix.discovery.shared.transport.jersey2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.util.InstanceInfoGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Regression test for registration against a server that rejects the request with a non-2xx
 * status and a response body (e.g. meshcontrol-eurekashim's 403 + explanatory text when an app
 * is blocked from classic registration). The client used to discard that body entirely; this
 * verifies it can now be read, without throwing, and without disturbing the reported status code.
 */
public class RegisterErrorResponseBodyTest {

    private static final String REJECTION_BODY =
            "classic eureka registration is not allowed for this app; use mesh-registration";

    private HttpServer server;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void registerSurfacesStatusCodeWhenServerRejectsWithABody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = REJECTION_BODY.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(403, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.start();

        TransportClientFactory clientFactory = Jersey2ApplicationClientFactory.newBuilder().build();
        EurekaHttpClient client = clientFactory.newClient(
                new DefaultEndpoint("http://localhost:" + server.getAddress().getPort() + "/"));
        try {
            EurekaHttpResponse<Void> response = client.register(InstanceInfoGenerator.takeOne());
            assertThat(response.getStatusCode(), is(equalTo(403)));
        } finally {
            client.shutdown();
        }
    }
}

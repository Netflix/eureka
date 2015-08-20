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

package com.netflix.eureka2.server.http;

import javax.ws.rs.core.MediaType;
import java.io.File;

import com.netflix.eureka2.rxnetty.HttpResponseUtils;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.Test;

import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class JarResourcesRequestHandlerTest {

    private static final String JAR_PATTERN = "rxnetty-.*";

    @Test
    public void testJarDiscovery() throws Exception {
        File result = JarResourcesRequestHandler.findJar(JAR_PATTERN);
        assertThat(result, is(notNullValue()));
        assertThat(result.getName().indexOf("rxnetty"), is(0));
        assertThat(result.exists(), is(true));
    }

    @Test
    public void testResourceDownload() throws Exception {
        EurekaHttpServer httpServer = new EurekaHttpServer(anEurekaServerTransportConfig().withHttpPort(0).build());
        httpServer.connectHttpEndpoint("/ui", new JarResourcesRequestHandler("/ui", JAR_PATTERN, null));
        httpServer.start();

        try {
            HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, "/ui/META-INF/MANIFEST.MF");
            String response = HttpResponseUtils.handleGetRequest(httpServer.serverPort(), request, MediaType.TEXT_PLAIN_TYPE);

            assertThat(response, is(notNullValue()));
        } finally {
            httpServer.stop();
        }
    }
}
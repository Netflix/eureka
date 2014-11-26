/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.config.EurekaDashboardConfig;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.RequestHandlerWithErrorMapper;
import io.reactivex.netty.protocol.http.server.file.FileErrorResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class DashboardHttpServer {

    private static final Logger logger = LoggerFactory.getLogger(DashboardHttpServer.class);

    private final EurekaDashboardConfig config;
    private HttpServer<ByteBuf, ByteBuf> server;

    @Inject
    public DashboardHttpServer(EurekaDashboardConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void start() {
        server = RxNetty.createHttpServer(config.getDashboardPort(),
                RequestHandlerWithErrorMapper.from(
                        new MainRequestHandler(),
                        new FileErrorResponseMapper())).start();
        logger.info("Starting HTTP dashboard server on port {}...", server.getServerPort());
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (server != null) {
            server.shutdown();
        }
    }
}

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

package com.netflix.eureka.server;

import com.google.inject.AbstractModule;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.name.Names;
import com.netflix.adminresources.resources.KaryonWebAdminModule;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import com.netflix.eureka.server.EurekaServer.EurekaServerModule;
import com.netflix.eureka.server.EurekaServer.EurekaWebModule;
import com.netflix.eureka.server.service.EurekaServiceImpl;
import com.netflix.eureka.service.EurekaService;
import com.netflix.governator.annotations.Modules;
import com.netflix.karyon.KaryonBootstrap;
import com.netflix.karyon.KaryonServer;
import com.netflix.karyon.Submodules;
import com.netflix.karyon.archaius.ArchaiusBootstrap;
import com.netflix.karyon.transport.ServerPort;
import com.netflix.karyon.transport.http.AbstractHttpModule;
import com.netflix.karyon.transport.http.HttpRequestRouter;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
@ArchaiusBootstrap
@KaryonBootstrap(name = "eureka-server")
@Modules(include = EurekaServerModule.class)
@Submodules(include = {
        EurekaWebModule.class,
        KaryonWebAdminModule.class,
        AvroRegistrationModule.class
})
public class EurekaServer extends KaryonServer {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServer.class);

    public EurekaServer() {
        super(EurekaServer.class);
    }

    public static void main(String[] args) {
        EurekaServer server = new EurekaServer();
        try {
            server.start();
            server.waitTillShutdown();
        } catch (Exception e) {
            logger.error("Error while starting Eureka server.", e);
            if (server != null) {
                server.shutdown();
            }
            System.exit(-1);
        }
        // In case we have non-daemon threads running
        System.exit(0);
    }

    public static class EurekaServerModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(ServerPort.class).annotatedWith(Names.named("shutdown")).toInstance(new ServerPort(8089));
            bind(EurekaService.class).to(EurekaServiceImpl.class);
            // TODO Fix registry construction.
            bind(EurekaRegistry.class).toInstance(new LeasedInstanceRegistry(new InstanceInfo()));
        }
    }

    public static class EurekaWebModule extends AbstractHttpModule<ByteBuf, ByteBuf> {
        public EurekaWebModule() {
            super(ByteBuf.class, ByteBuf.class);
        }

        @Override
        public int serverPort() {
            return 8080;
        }

        @Override
        protected void bindRequestRouter(AnnotatedBindingBuilder<HttpRequestRouter<ByteBuf, ByteBuf>> bind) {
            bind.to(EurekaHttpRouter.class);
        }
    }

    public static class EurekaHttpRouter implements HttpRequestRouter<ByteBuf, ByteBuf> {
        @Override
        public Observable<Void> route(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
            return response.close();
        }
    }
}
